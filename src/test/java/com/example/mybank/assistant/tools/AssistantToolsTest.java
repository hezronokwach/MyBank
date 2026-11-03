package com.example.mybank.assistant.tools;

import com.example.mybank.assistant.entity.AssistantToolCall;
import com.example.mybank.assistant.entity.ToolOutcome;
import com.example.mybank.assistant.repository.AssistantToolCallRepository;
import com.example.mybank.assistant.tools.AssistantTools.AccountView;
import com.example.mybank.assistant.tools.AssistantTools.ToolResult;
import com.example.mybank.assistant.tools.AssistantTools.TransactionView;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.services.impl.AccountServiceImpl;
import com.example.mybank.services.impl.TransactionQueryServiceImpl;
import com.example.mybank.util.AccountNumberGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the tools directly as one customer (Wanjiku), including with another customer's (Otieno's) data.
 * These prove the boundary holds whatever the model asks for.
 */
@DataJpaTest
@Import({
        AssistantToolsFactory.class, AccountResolver.class, AccountServiceImpl.class,
        TransactionQueryServiceImpl.class, AccountMapper.class, AccountNumberGenerator.class,
        AssistantToolsTest.Beans.class
})
class AssistantToolsTest {

    private static final String WANJIKU = "wanjiku@test.local";

    @TestConfiguration
    static class Beans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AssistantToolsFactory factory;

    @Autowired
    private AssistantToolCallRepository toolCalls;

    @Autowired
    private ObjectMapper objectMapper;

    private User wanjiku;
    private Account wanjikuCurrent;
    private Account wanjikuSavings;
    private Account otienoCurrent;
    private AssistantTools tools;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "Kamau", UserRole.ROLE_USER));
        User otieno = em.persist(new User("otieno@test.local", "hash", "Otieno", "Odhiambo", UserRole.ROLE_USER));
        wanjikuCurrent = account("1000004821", wanjiku, AccountType.CURRENT, "1500.00");
        wanjikuSavings = account("1000009310", wanjiku, AccountType.SAVINGS, "20000.00");
        otienoCurrent = account("1000007777", otieno, AccountType.CURRENT, "99999.00");
        tools = factory.forUser(WANJIKU);
    }

    @Test
    void listMyAccountsReturnsOnlyOwnAccountsMaskedAndNothingPersonal() throws Exception {
        ToolResult result = tools.listMyAccounts();

        assertThat(result.status()).isEqualTo(ToolOutcome.OK);
        @SuppressWarnings("unchecked")
        List<AccountView> accounts = (List<AccountView>) result.data();
        List<String> maskedNumbers = new ArrayList<>();
        for (AccountView account : accounts) {
            maskedNumbers.add(account.account());
        }
        assertThat(maskedNumbers).containsExactlyInAnyOrder("...4821", "...9310");

        String json = objectMapper.writeValueAsString(result);
        assertThat(json).doesNotContain("1000004821", "1000007777", "99999", WANJIKU, "Wanjiku", "Kamau", "pin-hash");
    }

    @Test
    void getAccountFindsOwnAccountByLast4OrFullNumber() {
        ToolResult byLast4 = tools.getAccount("9310");
        ToolResult byFullNumber = tools.getAccount("1000-004-821");

        assertThat(((AccountView) byLast4.data()).balance()).isEqualByComparingTo("20000.00");
        assertThat(((AccountView) byFullNumber.data()).account()).isEqualTo("...4821");
    }

    @Test
    void getAccountWithAnotherCustomersNumberIsNotFoundAndAudited() {
        ToolResult result = tools.getAccount("1000007777");

        assertThat(result.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(result.data()).isNull();
        assertThat(lastCall().getOutcome()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(lastCall().getToolName()).isEqualTo("getAccount");
        assertThat(lastCall().getArguments()).contains("1000007777");
    }

    @Test
    void getAccountRejectsTooShortInput() {
        assertThat(tools.getAccount("21").status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(tools.getAccount("").status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(tools.getAccount(null).status()).isEqualTo(ToolOutcome.NOT_FOUND);
    }

    @Test
    void sameLast4OnTwoOwnAccountsAsksWhichOne() {
        account("1000014821", wanjiku, AccountType.SAVINGS, "10.00");

        ToolResult result = tools.getAccount("4821");

        assertThat(result.status()).isEqualTo(ToolOutcome.AMBIGUOUS);
        assertThat(result.data()).isEqualTo(Map.of("options", List.of("...4821", "...4821")));
        assertThat(tools.getAccount("14821").status()).isEqualTo(ToolOutcome.OK);
    }

    @Test
    void getRecentTransactionsMasksCounterpartyAndCapsTheLimit() {
        for (int i = 0; i < 24; i++) {
            transaction("DEP10000" + (10 + i), null, wanjikuCurrent, "1");
        }
        transaction("TRN1000001", otienoCurrent, wanjikuCurrent, "50"); // newest

        ToolResult defaultLimit = tools.getRecentTransactions("4821", null);
        ToolResult tooMany = tools.getRecentTransactions("4821", 100);

        assertThat(transactionsOf(defaultLimit)).hasSize(5);
        assertThat(transactionsOf(tooMany)).hasSize(20);
        TransactionView transfer = null;
        for (TransactionView transaction : transactionsOf(tooMany)) {
            if (transaction.reference().equals("TRN1000001")) {
                transfer = transaction;
            }
        }
        assertThat(transfer).isNotNull();
        assertThat(transfer.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(transfer.counterparty()).isEqualTo("...7777");
        assertThat(transfer.currency()).isEqualTo("KES");
        assertThat(transfer.date()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})");
    }

    @Test
    void getRecentTransactionsWithAnotherCustomersNumberIsNotFound() {
        transaction("DEP1000001", null, otienoCurrent, "500");

        ToolResult result = tools.getRecentTransactions("1000007777", 5);

        assertThat(result.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(lastCall().getOutcome()).isEqualTo(ToolOutcome.NOT_FOUND);
    }

    @Test
    void findTransactionReturnsOwnAndHidesOthers() {
        transaction("TRN1000001", wanjikuCurrent, wanjikuSavings, "10");
        transaction("DEP1000002", null, otienoCurrent, "500");

        assertThat(tools.findTransaction("trn1000001 ").status()).isEqualTo(ToolOutcome.OK);
        ToolResult other = tools.findTransaction("DEP1000002");
        ToolResult unknown = tools.findTransaction("DEP9999999");

        assertThat(other.status()).isEqualTo(ToolOutcome.NOT_FOUND);
        assertThat(other.message()).isEqualTo(unknown.message());
    }

    @Test
    void sixthCallInOneAnswerIsDenied() {
        for (int i = 0; i < 5; i++) {
            assertThat(tools.listMyAccounts().status()).isEqualTo(ToolOutcome.OK);
        }

        ToolResult sixth = tools.getAccount("4821");

        assertThat(sixth.status()).isEqualTo(ToolOutcome.DENIED);
        assertThat(sixth.data()).isNull();
        assertThat(lastCall().getOutcome()).isEqualTo(ToolOutcome.DENIED);
        // A new answer gets a fresh budget
        assertThat(factory.forUser(WANJIKU).getAccount("4821").status()).isEqualTo(ToolOutcome.OK);
    }

    @Test
    void everyCallWritesAnAuditRowWithoutAMessageYet() {
        tools.listMyAccounts();
        tools.getAccount("9310");

        List<AssistantToolCall> calls = toolCalls.findByUserIdOrderByCreatedAtAsc(wanjiku.getId());
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).getToolName()).isEqualTo("listMyAccounts");
        assertThat(calls.get(1).getToolName()).isEqualTo("getAccount");

        List<UUID> savedIds = new ArrayList<>();
        for (AssistantToolCall call : calls) {
            assertThat(call.getMessageId()).isNull();
            assertThat(call.getDurationMs()).isGreaterThanOrEqualTo(0);
            assertThat(call.getCreatedAt()).isNotNull();
            savedIds.add(call.getId());
        }
        assertThat(tools.recordedCallIds()).isEqualTo(savedIds);
    }

    private AssistantToolCall lastCall() {
        List<AssistantToolCall> calls = toolCalls.findByUserIdOrderByCreatedAtAsc(wanjiku.getId());
        return calls.get(calls.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<TransactionView> transactionsOf(ToolResult result) {
        assertThat(result.status()).isEqualTo(ToolOutcome.OK);
        return (List<TransactionView>) ((Map<String, Object>) result.data()).get("transactions");
    }

    private Account account(String number, User owner, AccountType type, String balance) {
        Account account = em.persist(new Account(number, new BigDecimal(balance), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, type, "KES", owner, "pin-hash"));
        em.flush();
        return account;
    }

    private void transaction(String reference, Account from, Account to, String amount) {
        Instant before = Instant.now();
        while (!Instant.now().isAfter(before)) {
            Thread.onSpinWait();
        }
        TransactionType type;
        if (from == null) {
            type = TransactionType.DEPOSIT;
        } else if (to == null) {
            type = TransactionType.WITHDRAWAL;
        } else {
            type = TransactionType.TRANSFER;
        }
        em.persist(new Transaction(reference, from, to, new BigDecimal(amount), type, TransactionStatus.SUCCESS));
        em.flush();
    }
}
