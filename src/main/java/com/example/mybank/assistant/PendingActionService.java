package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.PendingActionView;
import com.example.mybank.assistant.entity.PendingAction;
import com.example.mybank.assistant.entity.PendingActionStatus;
import com.example.mybank.assistant.entity.PendingActionType;
import com.example.mybank.assistant.repository.PendingActionRepository;
import com.example.mybank.assistant.tools.AccountResolver;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.BaseException;
import com.example.mybank.exceptions.PendingActionConflictException;
import com.example.mybank.exceptions.PendingActionNotFoundException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pending actions: things the assistant prepared that only happen when the customer confirms.
 * The AI is never involved in confirming: the app calls the confirm endpoint directly.
 */
@Service
public class PendingActionService {

    static final Duration TIME_TO_CONFIRM = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(PendingActionService.class);

    private final PendingActionRepository pendingActionRepository;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final AssistantMetrics metrics;

    public PendingActionService(PendingActionRepository pendingActionRepository,
                                UserRepository userRepository,
                                AccountService accountService,
                                AssistantMetrics metrics) {
        this.pendingActionRepository = pendingActionRepository;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.metrics = metrics;
    }

    // Called by the requestFreezeAccount tool. It only saves the request; nothing is frozen yet.
    public PendingAction createFreeze(UUID userId, UUID conversationId, String accountNumber, String reason) {
        String summary = "Freeze account " + AccountResolver.mask(accountNumber);
        Instant expiresAt = Instant.now().plus(TIME_TO_CONFIRM);

        PendingAction action = new PendingAction(userId, conversationId, PendingActionType.FREEZE_ACCOUNT,
                accountNumber, shorten(reason), summary, expiresAt);
        PendingAction saved = pendingActionRepository.save(action);
        metrics.recordAction(saved.getType(), saved.getStatus());
        return saved;
    }

    /**
     * POST /api/v1/assistant/actions/{id}/confirm
     *
     * No @Transactional on purpose: each save below commits straight away. That way the action is marked
     * as taken before the freeze runs, and an EXPIRED or FAILED status is kept even though an error follows.
     */
    public PendingActionView confirm(String email, UUID actionId) {
        // 1. Load the action, only if it belongs to this user
        PendingAction action = loadOwnAction(email, actionId);

        // 2. It must still be waiting
        if (action.getStatus() != PendingActionStatus.PENDING) {
            throw new PendingActionConflictException("This request was already " + action.getStatus().name().toLowerCase());
        }

        // 3. It must not have run out of time
        if (Instant.now().isAfter(action.getExpiresAt())) {
            action.setStatus(PendingActionStatus.EXPIRED);
            action.setDecidedAt(Instant.now());
            pendingActionRepository.save(action);
            metrics.recordAction(action.getType(), PendingActionStatus.EXPIRED);
            throw new PendingActionConflictException("This request has expired. Ask the assistant again.");
        }

        // 4. Mark it as confirmed BEFORE doing anything. If two confirms arrive at the same moment,
        //    the version column lets only the first save succeed, so the action can't run twice.
        action.setStatus(PendingActionStatus.CONFIRMED);
        action.setDecidedAt(Instant.now());
        try {
            action = pendingActionRepository.saveAndFlush(action);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PendingActionConflictException("This request is already being handled");
        }

        // 5. Do it, through the normal account service with its normal checks
        try {
            if (action.getType() == PendingActionType.FREEZE_ACCOUNT) {
                accountService.freezeMyAccount(email, action.getAccountNumber(), action.getReason());
                action.setResult("Account " + AccountResolver.mask(action.getAccountNumber())
                        + " is frozen. To unfreeze it, contact support.");
            }
        } catch (BaseException e) {
            // For example the account was frozen or closed in the meantime
            action.setStatus(PendingActionStatus.FAILED);
            action.setResult(shorten(e.getMessage()));
        } catch (RuntimeException e) {
            // Anything unexpected: record it as failed so it never looks done when it isn't
            log.error("Pending action {} failed: {}", action.getId(), e.getMessage());
            action.setStatus(PendingActionStatus.FAILED);
            action.setResult("Something went wrong. Nothing was changed. Please try again.");
        }

        PendingAction saved = pendingActionRepository.save(action);
        metrics.recordAction(saved.getType(), saved.getStatus());
        log.info("Pending action {} {} for user {}", saved.getId(), saved.getStatus(), saved.getUserId());
        return toView(saved);
    }

    // POST /api/v1/assistant/actions/{id}/cancel
    public PendingActionView cancel(String email, UUID actionId) {
        PendingAction action = loadOwnAction(email, actionId);

        if (action.getStatus() != PendingActionStatus.PENDING) {
            throw new PendingActionConflictException("This request was already " + action.getStatus().name().toLowerCase());
        }

        action.setStatus(PendingActionStatus.CANCELLED);
        action.setDecidedAt(Instant.now());
        action.setResult("Cancelled. Nothing was changed.");
        PendingAction saved;
        try {
            saved = pendingActionRepository.saveAndFlush(action);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new PendingActionConflictException("This request is already being handled");
        }
        metrics.recordAction(saved.getType(), saved.getStatus());
        return toView(saved);
    }

    // Every minute: actions nobody confirmed in time become EXPIRED
    @Scheduled(fixedRate = 60_000)
    public void expireOldActions() {
        List<PendingAction> expired = pendingActionRepository
                .findByStatusAndExpiresAtBefore(PendingActionStatus.PENDING, Instant.now());

        for (PendingAction action : expired) {
            action.setStatus(PendingActionStatus.EXPIRED);
            action.setDecidedAt(Instant.now());
            action.setResult("Expired. Nothing was changed.");
        }

        if (!expired.isEmpty()) {
            try {
                pendingActionRepository.saveAll(expired);
                for (PendingAction action : expired) {
                    metrics.recordAction(action.getType(), PendingActionStatus.EXPIRED);
                }
                log.info("Expired {} pending actions", expired.size());
            } catch (ObjectOptimisticLockingFailureException e) {
                // A customer confirmed or cancelled one at the same moment; the next run picks up the rest
                log.info("Skipped expiring pending actions that changed meanwhile");
            }
        }
    }

    public PendingActionView toView(PendingAction action) {
        return new PendingActionView(action.getId(), action.getType(), action.getSummary(),
                action.getStatus(), action.getExpiresAt(), action.getResult());
    }

    // Someone else's action id gives the same 404 as an id that doesn't exist
    private PendingAction loadOwnAction(String email, UUID actionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        return pendingActionRepository.findByIdAndUserId(actionId, user.getId())
                .orElseThrow(() -> new PendingActionNotFoundException("Pending action " + actionId + " not found"));
    }

    // Keeps text within the 200-character columns
    private String shorten(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() > 200) {
            return text.substring(0, 200);
        }
        return text;
    }
}
