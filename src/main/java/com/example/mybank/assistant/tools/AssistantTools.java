package com.example.mybank.assistant.tools;

import com.example.mybank.assistant.entity.AssistantToolCall;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The only things the model can do. One instance per request, bound to the logged-in customer:
 * no tool takes a user or email argument, so the model can't choose whose data to read.
 * Results carry masked account numbers and only the fields an answer needs.
 */
public class AssistantTools {

    static final int MAX_CALLS_PER_ANSWER = 5;
    static final int MAX_TRANSACTIONS = 20;

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    // What the model gets back from every tool
    public record ToolResult(ToolOutcome status, String message, Object data) {
        static ToolResult ok(Object data) {
            return new ToolResult(ToolOutcome.OK, null, data);
        }
    }

    // Dates are sent as text with their offset ("2026-11-03T23:31+03:00") so the model reads them as written
    public record AccountView(String account, AccountType type, String currency, BigDecimal balance,
                              AccountStatus status, String openedAt) {}

    public record TransactionView(String date, TransactionType type, BigDecimal amount, String currency,
                                  TransactionDirection direction, String counterparty, String reference,
                                  TransactionStatus status) {}

    private final String email;
    private final UUID userId;
    private final AccountService accountService;
    private final TransactionQueryService transactionQueryService;
    private final AccountResolver accountResolver;
    private final AssistantToolCallRepository toolCallRepository;
    private final ObjectMapper objectMapper;

    private int callCount = 0;
    private final List<UUID> recordedCallIds = new ArrayList<>();

    AssistantTools(String email, UUID userId, AccountService accountService,
                   TransactionQueryService transactionQueryService, AccountResolver accountResolver,
                   AssistantToolCallRepository toolCallRepository, ObjectMapper objectMapper) {
        this.email = email;
        this.userId = userId;
        this.accountService = accountService;
        this.transactionQueryService = transactionQueryService;
        this.accountResolver = accountResolver;
        this.toolCallRepository = toolCallRepository;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "listMyAccounts",
            description = "Lists all of the customer's MyBank accounts with masked number (last 4 digits), "
                    + "type, currency, balance and status. Use it for balance questions and to find which account "
                    + "the customer means.")
    public ToolResult listMyAccounts() {
        return run("listMyAccounts", Map.of(), () ->
                ToolResult.ok(accountService.getMyAccounts(email).stream().map(AssistantTools::toView).toList()));
    }

    @Tool(name = "getAccount",
            description = "Returns one of the customer's accounts: masked number, type, currency, balance, status "
                    + "and opening date.")
    public ToolResult getAccount(
            @ToolParam(description = "The account's last 4 (or more) digits, or the full account number")
            String accountNumberOrLast4) {
        return run("getAccount", args("accountNumberOrLast4", accountNumberOrLast4), () -> {
            AccountResolver.Resolution resolution = accountResolver.resolve(email, accountNumberOrLast4);
            ToolResult unresolved = unresolved(resolution);
            return unresolved != null ? unresolved : ToolResult.ok(toView(resolution.account()));
        });
    }

    @Tool(name = "getRecentTransactions",
            description = "Returns up to 20 of the most recent transactions on one of the customer's accounts, "
                    + "newest first: date, type, amount, direction (IN or OUT), the other account's last 4 digits "
                    + "and reference.")
    public ToolResult getRecentTransactions(
            @ToolParam(description = "The account's last 4 (or more) digits, or the full account number")
            String accountNumberOrLast4,
            @ToolParam(description = "How many transactions to return, from 1 to 20", required = false)
            Integer limit) {
        Map<String, Object> arguments = args("accountNumberOrLast4", accountNumberOrLast4);
        arguments.put("limit", limit);
        return run("getRecentTransactions", arguments, () -> {
            AccountResolver.Resolution resolution = accountResolver.resolve(email, accountNumberOrLast4);
            ToolResult unresolved = unresolved(resolution);
            if (unresolved != null) {
                return unresolved;
            }
            int size = limit == null ? 5 : Math.max(1, Math.min(limit, MAX_TRANSACTIONS));
            List<TransactionView> transactions = transactionQueryService
                    .getRecent(email, resolution.account().accountNumber(), size)
                    .stream().map(AssistantTools::toView).toList();
            return ToolResult.ok(Map.of("account", AccountResolver.mask(resolution.account().accountNumber()),
                    "transactions", transactions));
        });
    }

    @Tool(name = "findTransaction",
            description = "Finds one transaction by its reference (for example TRN, DEP or WDR followed by "
                    + "7 digits), only if it involves one of the customer's accounts.")
    public ToolResult findTransaction(
            @ToolParam(description = "The transaction reference, such as TRN1234567") String reference) {
        return run("findTransaction", args("reference", reference), () -> {
            String normalised = reference == null ? "" : reference.trim().toUpperCase();
            return ToolResult.ok(toView(transactionQueryService.findByReference(email, normalised)));
        });
    }

    /** Ids of the audit rows written for this answer, so the chat endpoint can link them to its message. */
    public List<UUID> recordedCallIds() {
        return Collections.unmodifiableList(recordedCallIds);
    }

    // Every tool goes through here: limit, error handling, audit row
    private ToolResult run(String toolName, Map<String, Object> arguments, Supplier<ToolResult> body) {
        long start = System.currentTimeMillis();
        callCount++;
        ToolResult result;
        if (callCount > MAX_CALLS_PER_ANSWER) {
            result = new ToolResult(ToolOutcome.DENIED, "Tool limit reached. Answer with what you have.", null);
        } else {
            try {
                result = body.get();
            } catch (AccountNotFoundException | TransactionNotFoundException e) {
                result = new ToolResult(ToolOutcome.NOT_FOUND,
                        "No such account or transaction for this customer.", null);
            } catch (RuntimeException e) {
                log.warn("Assistant tool {} failed: {}", toolName, e.getMessage());
                result = new ToolResult(ToolOutcome.ERROR,
                        "That check failed. Suggest the customer try again later.", null);
            }
        }
        audit(toolName, arguments, result.status(), System.currentTimeMillis() - start);
        return result;
    }

    private void audit(String toolName, Map<String, Object> arguments, ToolOutcome outcome, long durationMs) {
        try {
            AssistantToolCall call = toolCallRepository.save(new AssistantToolCall(
                    userId, toolName, objectMapper.writeValueAsString(arguments), outcome, durationMs));
            recordedCallIds.add(call.getId());
        } catch (Exception e) {
            // A failed audit write must not break the customer's answer, but it must be visible
            log.error("Could not save audit row for tool {}: {}", toolName, e.getMessage());
        }
    }

    private static ToolResult unresolved(AccountResolver.Resolution resolution) {
        return switch (resolution.status()) {
            case FOUND -> null;
            case NOT_FOUND -> new ToolResult(ToolOutcome.NOT_FOUND, "No such account for this customer.", null);
            case AMBIGUOUS -> new ToolResult(ToolOutcome.AMBIGUOUS,
                    "More than one of the customer's accounts matches. Ask which one.",
                    Map.of("options", resolution.maskedOptions()));
        };
    }

    private static Map<String, Object> args(String name, Object value) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(name, value);
        return arguments;
    }

    private static AccountView toView(AccountResponse account) {
        return new AccountView(AccountResolver.mask(account.accountNumber()), account.type(), account.currency(),
                account.balance(), account.status(), formatDate(account.createdAt()));
    }

    private static TransactionView toView(TransactionHistoryResponse transaction) {
        return new TransactionView(formatDate(transaction.date()), transaction.type(), transaction.amount(),
                transaction.currency(), transaction.direction(),
                transaction.counterpartyLast4() == null ? null : "..." + transaction.counterpartyLast4(),
                transaction.reference(), transaction.status());
    }

    private static String formatDate(Instant instant) {
        // Same zone as the rest of MyBank (AccountMapper's interest day)
        return instant == null ? null
                : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime().truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
