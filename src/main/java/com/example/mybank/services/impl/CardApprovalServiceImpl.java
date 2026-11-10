package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.AdminCardResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.Transaction;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.services.CardApprovalService;
import com.example.mybank.util.CardEncryptor;
import com.example.mybank.util.CardNumberGenerator;
import com.example.mybank.util.ReferenceGenerator;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The admin approves a card (it gets a number, CVV and expiry) or rejects it (the fee goes back).
 * Only a card that is still waiting can be approved or rejected, and only once.
 */
@Service
public class CardApprovalServiceImpl implements CardApprovalService {

    static final int YEARS_VALID = 3;
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CardNumberGenerator cardNumberGenerator;
    private final CardEncryptor cardEncryptor;
    private final ReferenceGenerator referenceGenerator;

    public CardApprovalServiceImpl(CardRepository cardRepository,
                                   AccountRepository accountRepository,
                                   TransactionRepository transactionRepository,
                                   CardNumberGenerator cardNumberGenerator,
                                   CardEncryptor cardEncryptor,
                                   ReferenceGenerator referenceGenerator) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.cardNumberGenerator = cardNumberGenerator;
        this.cardEncryptor = cardEncryptor;
        this.referenceGenerator = referenceGenerator;
    }

    // GET /api/v1/admin/cards?status=...
    @Override
    @Transactional(readOnly = true)
    public List<AdminCardResponse> listCards(CardStatus status) {
        List<Card> cards;
        if (status == null) {
            cards = cardRepository.findAllByOrderByCreatedAtDesc();
        } else {
            cards = cardRepository.findByStatusOrderByCreatedAtAsc(status);
        }

        List<AdminCardResponse> result = new ArrayList<>();
        for (Card card : cards) {
            result.add(toResponse(card));
        }
        return result;
    }

    /**
     * POST /api/v1/admin/cards/{id}/approve
     * Creates the card number, CVV and expiry. The number and CVV are saved encrypted.
     */
    @Override
    @Transactional
    public AdminCardResponse approve(String adminEmail, UUID cardId) {
        Card card = loadWaitingCard(cardId);

        String cardNumber = cardNumberGenerator.generateCardNumber();
        String cvv = cardNumberGenerator.generateCvv();
        LocalDate expires = LocalDate.now(NAIROBI).plusYears(YEARS_VALID);

        card.setCardNumberEncrypted(cardEncryptor.encrypt(cardNumber));
        card.setCvvEncrypted(cardEncryptor.encrypt(cvv));
        card.setLast4(cardNumber.substring(cardNumber.length() - 4));
        card.setExpiryMonth(expires.getMonthValue());
        card.setExpiryYear(expires.getYear());
        card.setStatus(CardStatus.ACTIVE);
        card.setApprovedAt(Instant.now());
        card.setDecidedBy(adminEmail);

        return toResponse(saveOnce(card));
    }

    /**
     * POST /api/v1/admin/cards/{id}/reject
     * The card is refused and the fee goes back to the account it came from.
     */
    @Override
    @Transactional
    public AdminCardResponse reject(String adminEmail, UUID cardId, String reason) {
        Card card = loadWaitingCard(cardId);

        card.setStatus(CardStatus.REJECTED);
        card.setRejectionReason(reason.trim());
        card.setRejectedAt(Instant.now());
        card.setDecidedBy(adminEmail);
        // Saved first: if another admin already decided this card, we stop here and nothing is refunded
        Card saved = saveOnce(card);

        // Give the fee back, with the account locked so its balance can't change under us
        Account account = accountRepository.findAndLockByAccountNumber(saved.getAccount().getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        account.setBalance(account.getBalance().add(saved.getFeePaid()));
        accountRepository.save(account);
        transactionRepository.save(new Transaction(referenceGenerator.generateCardRefund(), null, account,
                saved.getFeePaid(), TransactionType.CARD_FEE_REFUND, TransactionStatus.SUCCESS));

        return toResponse(saved);
    }

    private Card loadWaitingCard(UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        if (card.getStatus() != CardStatus.PENDING_APPROVAL) {
            throw new CardStatusConflictException("This card was already " + card.getStatus().name().toLowerCase());
        }
        return card;
    }

    // The version column: if two admins decide the same card at the same moment, only the first save succeeds
    private Card saveOnce(Card card) {
        try {
            return cardRepository.saveAndFlush(card);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CardStatusConflictException("Another admin is handling this card");
        }
    }

    private AdminCardResponse toResponse(Card card) {
        String name = card.getUser().getFirstName() + " " + card.getUser().getLastName();
        BigDecimal fee = card.getFeePaid().setScale(2, RoundingMode.HALF_UP);
        return new AdminCardResponse(card.getId(), card.getStatus(), name, card.getUser().getEmail(),
                card.getAccount().getAccountNumber(), fee, card.getLast4(), card.getExpiryMonth(),
                card.getExpiryYear(), card.getCreatedAt(), card.getDecidedBy(), card.getRejectionReason());
    }
}
