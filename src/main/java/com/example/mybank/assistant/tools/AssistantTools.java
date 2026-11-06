package com.example.mybank.assistant.tools;

import com.example.mybank.assistant.PendingActionService;
import com.example.mybank.assistant.dto.PendingActionView;
import com.example.mybank.assistant.entity.AssistantToolCall;
import com.example.mybank.assistant.entity.PendingAction;
import com.example.mybank.assistant.entity.ToolOutcome;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.dto.responses.TransactionHistoryResponse;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.TransactionNotFoundException;
import com.example.mybank.services.AccountService;
import com.example.mybank.services.TransactionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only things the AI model can do.
 *
 * One AssistantTools object is created for each answer, for the logged-in customer.
 * No tool takes a user or email argument, so the model can't choose whose data to read.
 *
 * Every tool follows the same steps:
 *   1. stop if this answer has already used 5 tool calls
 *   2. do the work
 *   3. turn any error into a short message (never throw back to the model)
 *   4. save an audit row in assistant_tool_calls
 */
public class AssistantTools {

    static final int MAX_CALLS_PER_ANSWER = 5;
    static final int MAX_TRANSACTIONS = 20;
    static final int DEFAULT_TRANSACTIONS = 5;

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    // What the model gets back from every tool
    public record ToolResult(ToolOutcome status, String message, Object data) {}

    // An account as the model sees it: masked number, no personal details
    public record AccountView(String account, AccountType type, String currency, BigDecimal balance,
                              AccountStatus status, String openedAt) {}

    // A transaction as the model sees it
    public record TransactionView(String date, TransactionType type, BigDecimal amount, String currency,
                                  TransactionDirection direction, String counterparty, String reference,
                                  TransactionStatus status) {}

    private final String email;
    private final UUID userId;
    private final UUID conversationId;
    private final AccountService accountService;
    private final TransactionQueryService transactionQueryService;
    private final AccountResolver accountResolver;
    private final AssistantToolCallRepository toolCallRepository;
    private final ObjectMapper objectMapper;
    private final PendingActionService pendingActionService;

    private int callCount = 0;
    private final List<UUID> recordedCallIds = new ArrayList<>();
    private final List<PendingActionView> createdPendingActions = new ArrayList<>();

    AssistantTools(String email, UUID userId, UUID conversationId, AccountService accountService,
                   TransactionQueryService transactionQueryService, AccountResolver accountResolver,
                   AssistantToolCallRepository toolCallRepository, ObjectMapper objectMapper,
                   PendingActionService pendingActionService) {
        this.email = email;
        this.userId = userId;
        this.conversationId = conversationId;
        this.accountService = accountService;
        this.transactionQueryService = transactionQueryService;
        this.accountResolver = accountResolver;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
        this.pendingActionService = pendingActionService;
    }

    // =========================================================================
    // TOOLS
    // =========================================================================

    @Tool(name = "listMyAccounts",
            description = "Lists all of the customer's MyBank accounts with masked number (last 4 digits), "
                    + "type, currency, balance and status. Use it for balance questions and to find which account "
                    + "the customer means.")
    public ToolResult listMyAccounts() {
        long start = System.currentTimeMillis();
        Map<String, Object> arguments = new LinkedHashMap<>();

        if (isOverCallLimit()) {
            return finish("listMyAccounts", arguments, deniedResult(), start);
        }

        ToolResult result;
        try {
            List<AccountResponse> accounts = accountService.getMyAccounts(email);

            List<AccountView> accountViews = new ArrayList<>();
            for (AccountResponse account : accounts) {
                accountViews.add(toAccountView(account));
            }

            result = new ToolResult(ToolOutcome.OK, null, accountViews);
        } catch (RuntimeException e) {
            result = errorResult("listMyAccounts", e);
        }

        return finish("listMyAccounts", arguments, result, start);
    }

    @Tool(name = "getAccount",
            description = "Returns one of the customer's accounts: masked number, type, currency, balance, status "
                    + "and opening date.")
    public ToolResult getAccount(
            @ToolParam(description = "The account's last 4 (or more) digits, or the full account number")
            String accountNumberOrLast4) {
        long start = System.currentTimeMillis();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("accountNumberOrLast4", accountNumberOrLast4);

        if (isOverCallLimit()) {
            return finish("getAccount", arguments, deniedResult(), start);
        }

        ToolResult result;
        try {
            AccountResolver.Resolution resolution = accountResolver.resolve(email, accountNumberOrLast4);

            if (resolution.status() == AccountResolver.Status.FOUND) {
                result = new ToolResult(ToolOutcome.OK, null, toAccountView(resolution.account()));
            } else {
                result = unresolvedResult(resolution);
            }
        } catch (RuntimeException e) {
            result = errorResult("getAccount", e);
        }

        return finish("getAccount", arguments, result, start);
    }

    @Tool(name = "getRecentTransactions",
            description = "Returns up to 20 of the most recent transactions on one of the customer's accounts, "
                    + "newest first: date, type, amount, currency, direction (IN or OUT), the other account's "
                    + "last 4 digits and reference.")
    public ToolResult getRecentTransactions(
            @ToolParam(description = "The account's last 4 (or more) digits, or the full account number")
            String accountNumberOrLast4,
            @ToolParam(description = "How many transactions to return, from 1 to 20", required = false)
            Integer limit) {
        long start = System.currentTimeMillis();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("accountNumberOrLast4", accountNumberOrLast4);
        arguments.put("limit", limit);

        if (isOverCallLimit()) {
            return finish("getRecentTransactions", arguments, deniedResult(), start);
        }

        ToolResult result;
        try {
            AccountResolver.Resolution resolution = accountResolver.resolve(email, accountNumberOrLast4);

            if (resolution.status() == AccountResolver.Status.FOUND) {
                // The model may leave the limit out or ask for too many
                int howMany = DEFAULT_TRANSACTIONS;
                if (limit != null) {
                    howMany = limit;
                }
                if (howMany < 1) {
                    howMany = 1;
                }
                if (howMany > MAX_TRANSACTIONS) {
                    howMany = MAX_TRANSACTIONS;
                }

                String accountNumber = resolution.account().accountNumber();
                List<TransactionHistoryResponse> transactions =
                        transactionQueryService.getRecent(email, accountNumber, howMany);

                List<TransactionView> transactionViews = new ArrayList<>();
                for (TransactionHistoryResponse transaction : transactions) {
                    transactionViews.add(toTransactionView(transaction));
                }

                Map<String, Object> data = new HashMap<>();
                data.put("account", AccountResolver.mask(accountNumber));
                data.put("transactions", transactionViews);
                result = new ToolResult(ToolOutcome.OK, null, data);
            } else {
                result = unresolvedResult(resolution);
            }
        } catch (AccountNotFoundException e) {
            result = notFoundResult();
        } catch (RuntimeException e) {
            result = errorResult("getRecentTransactions", e);
        }

        return finish("getRecentTransactions", arguments, result, start);
    }

    @Tool(name = "findTransaction",
            description = "Finds one transaction by its reference (for example TRN, DEP or WDR followed by "
                    + "7 digits), only if it involves one of the customer's accounts.")
    public ToolResult findTransaction(
            @ToolParam(description = "The transaction reference, such as TRN1234567") String reference) {
        long start = System.currentTimeMillis();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("reference", reference);

        if (isOverCallLimit()) {
            return finish("findTransaction", arguments, deniedResult(), start);
        }

        ToolResult result;
        try {
            // References are stored in capitals, such as TRN1234567
            String cleanReference = "";
            if (reference != null) {
                cleanReference = reference.trim().toUpperCase();
            }

            TransactionHistoryResponse transaction = transactionQueryService.findByReference(email, cleanReference);
            result = new ToolResult(ToolOutcome.OK, null, toTransactionView(transaction));
        } catch (TransactionNotFoundException e) {
            result = notFoundResult();
        } catch (RuntimeException e) {
            result = errorResult("findTransaction", e);
        }

        return finish("findTransaction", arguments, result, start);
    }

    @Tool(name = "requestFreezeAccount",
            description = "Prepares a freeze of one of the customer's ACTIVE accounts, for example after a lost "
                    + "phone. It does NOT freeze anything: it creates a request that the customer must confirm "
                    + "within 5 minutes on the card shown below your answer. Tell the customer to press Confirm on "
                    + "that card; never say the account is frozen.")
    public ToolResult requestFreezeAccount(
            @ToolParam(description = "The account's last 4 (or more) digits, or the full account number")
            String accountNumberOrLast4,
            @ToolParam(description = "Why the customer wants to freeze it, in a few words", required = false)
            String reason) {
        long start = System.currentTimeMillis();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("accountNumberOrLast4", accountNumberOrLast4);
        arguments.put("reason", reason);

        if (isOverCallLimit()) {
            return finish("requestFreezeAccount", arguments, deniedResult(), start);
        }

        ToolResult result;
        try {
            AccountResolver.Resolution resolution = accountResolver.resolve(email, accountNumberOrLast4);

            if (resolution.status() != AccountResolver.Status.FOUND) {
                result = unresolvedResult(resolution);
            } else if (resolution.account().status() != AccountStatus.ACTIVE) {
                result = new ToolResult(ToolOutcome.DENIED,
                        "This account isn't active, so it can't be frozen. For help, contact support.", null);
            } else {
                // Only saves a request; the account is frozen later, if the customer confirms
                PendingAction action = pendingActionService.createFreeze(
                        userId, conversationId, resolution.account().accountNumber(), reason);
                PendingActionView view = pendingActionService.toView(action);
                createdPendingActions.add(view);

                Map<String, Object> data = new HashMap<>();
                data.put("summary", view.summary());
                data.put("status", "WAITING_FOR_CUSTOMER_TO_CONFIRM");
                data.put("expiresInMinutes", 5);
                result = new ToolResult(ToolOutcome.OK,
                        "Nothing is frozen yet. Tell the customer to press Confirm on the card below your answer.", data);
            }
        } catch (RuntimeException e) {
            result = errorResult("requestFreezeAccount", e);
        }

        return finish("requestFreezeAccount", arguments, result, start);
    }

    /**
     * Pending actions created during this answer, so the chat endpoint can show a confirm card for each.
     */
    public List<PendingActionView> createdPendingActions() {
        return new ArrayList<>(createdPendingActions);
    }

    /**
     * Ids of the audit rows written during this answer, so the chat endpoint can link them to its message.
     */
    public List<UUID> recordedCallIds() {
        return new ArrayList<>(recordedCallIds);
    }

    // =========================================================================
    // STEPS SHARED BY EVERY TOOL
    // =========================================================================

    // Counts this call and says whether the answer has gone over its limit
    private boolean isOverCallLimit() {
        callCount = callCount + 1;
        return callCount > MAX_CALLS_PER_ANSWER;
    }

    // Saves the audit row, then hands the result back to the model
    private ToolResult finish(String toolName, Map<String, Object> arguments, ToolResult result, long start) {
        long durationMs = System.currentTimeMillis() - start;
        saveAuditRow(toolName, arguments, result.status(), durationMs);
        return result;
    }

    private void saveAuditRow(String toolName, Map<String, Object> arguments, ToolOutcome outcome, long durationMs) {
        try {
            String argumentsJson = objectMapper.writeValueAsString(arguments);
            AssistantToolCall call = new AssistantToolCall(userId, toolName, argumentsJson, outcome, durationMs);
            AssistantToolCall savedCall = toolCallRepository.save(call);
            recordedCallIds.add(savedCall.getId());
        } catch (Exception e) {
            // A failed audit write must not break the customer's answer, but it must show in the logs
            log.error("Could not save audit row for tool {}: {}", toolName, e.getMessage());
        }
    }

    private ToolResult deniedResult() {
        return new ToolResult(ToolOutcome.DENIED, "Tool limit reached. Answer with what you have.", null);
    }

    private ToolResult notFoundResult() {
        return new ToolResult(ToolOutcome.NOT_FOUND, "No such account or transaction for this customer.", null);
    }

    private ToolResult errorResult(String toolName, RuntimeException e) {
        log.warn("Assistant tool {} failed: {}", toolName, e.getMessage());
        return new ToolResult(ToolOutcome.ERROR, "That check failed. Suggest the customer try again later.", null);
    }

    // For when the account the model asked for isn't exactly one of the customer's accounts
    private ToolResult unresolvedResult(AccountResolver.Resolution resolution) {
        if (resolution.status() == AccountResolver.Status.AMBIGUOUS) {
            Map<String, Object> data = new HashMap<>();
            data.put("options", resolution.maskedOptions());
            return new ToolResult(ToolOutcome.AMBIGUOUS,
                    "More than one of the customer's accounts matches. Ask which one.", data);
        }
        return new ToolResult(ToolOutcome.NOT_FOUND, "No such account for this customer.", null);
    }

    // =========================================================================
    // TURNING SERVICE RESULTS INTO WHAT THE MODEL SEES
    // =========================================================================

    private AccountView toAccountView(AccountResponse account) {
        return new AccountView(
                AccountResolver.mask(account.accountNumber()),
                account.type(),
                account.currency(),
                toMoney(account.balance()),
                account.status(),
                formatDate(account.createdAt())
        );
    }

    private TransactionView toTransactionView(TransactionHistoryResponse transaction) {
        // Deposits and withdrawals have no other account
        String counterparty = null;
        if (transaction.counterpartyLast4() != null) {
            counterparty = "..." + transaction.counterpartyLast4();
        }

        return new TransactionView(
                formatDate(transaction.date()),
                transaction.type(),
                toMoney(transaction.amount()),
                transaction.currency(),
                transaction.direction(),
                counterparty,
                transaction.reference(),
                transaction.status()
        );
    }

    // The database keeps 4 decimals (1000.0000). The model gets 2 (1000.00): shorter, and how people read money.
    private BigDecimal toMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // Dates are sent as text with their time zone, for example "2026-11-03T23:31+03:00",
    // so the model reads them exactly as written. Same zone as AccountMapper's interest day.
    private String formatDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        OffsetDateTime localTime = instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        return localTime.truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
