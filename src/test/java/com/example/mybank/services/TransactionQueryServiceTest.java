package com.example.mybank.services;

import com.example.mybank.dto.responses.TransactionHistoryResponse;
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
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.TransactionNotFoundException;
import com.example.mybank.services.impl.TransactionQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TransactionQueryServiceImpl.class)
class TransactionQueryServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String OTIENO = "otieno@test.local";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TransactionQueryService service;

    private Account wanjikuCurrent;
    private Account wanjikuSavings;
    private Account otienoCurrent;

    @BeforeEach
    void seed() {
        User wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User otieno = em.persist(new User(OTIENO, "hash", "Otieno", "O", UserRole.ROLE_USER));
        wanjikuCurrent = account("1000004821", wanjiku);
        wanjikuSavings = account("1000009310", wanjiku);
        otienoCurrent = account("1000007777", otieno);
    }

    @Test
    void getRecentReturnsOwnAccountTransactionsNewestFirst() {
        transaction("DEP1000001", null, wanjikuCurrent, "500", TransactionType.DEPOSIT);
        transaction("TRN1000002", wanjikuCurrent, otienoCurrent, "200", TransactionType.TRANSFER);
        transaction("TRN1000003", otienoCurrent, wanjikuCurrent, "50", TransactionType.TRANSFER);
        transaction("DEP1000004", null, wanjikuSavings, "999", TransactionType.DEPOSIT); // other account

        List<TransactionHistoryResponse> history = service.getRecent(WANJIKU, "1000004821", 10);

        assertThat(history).extracting(TransactionHistoryResponse::reference)
                .containsExactly("TRN1000003", "TRN1000002", "DEP1000001");

        TransactionHistoryResponse incoming = history.get(0);
        assertThat(incoming.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(incoming.counterpartyLast4()).isEqualTo("7777");
        assertThat(incoming.amount()).isEqualByComparingTo("50");
        assertThat(incoming.currency()).isEqualTo("KES");
        assertThat(incoming.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(incoming.date()).isNotNull();

        TransactionHistoryResponse outgoing = history.get(1);
        assertThat(outgoing.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(outgoing.counterpartyLast4()).isEqualTo("7777");
    }

    @Test
    void getRecentRejectsAnotherCustomersAccount() {
        transaction("DEP1000001", null, otienoCurrent, "500", TransactionType.DEPOSIT);

        assertThatThrownBy(() -> service.getRecent(WANJIKU, "1000007777", 10))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getRecentCapsLimitAtTwenty() {
        for (int i = 0; i < 25; i++) {
            transaction("DEP10000" + (10 + i), null, wanjikuCurrent, "1", TransactionType.DEPOSIT);
        }

        assertThat(service.getRecent(WANJIKU, "1000004821", 100)).hasSize(20);
        assertThat(service.getRecent(WANJIKU, "1000004821", 0)).hasSize(1);
    }

    @Test
    void depositIsIncomingWithNoCounterparty() {
        transaction("DEP1000001", null, wanjikuCurrent, "500", TransactionType.DEPOSIT);

        TransactionHistoryResponse deposit = service.getRecent(WANJIKU, "1000004821", 5).get(0);

        assertThat(deposit.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(deposit.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(deposit.counterpartyLast4()).isNull();
    }

    @Test
    void withdrawalIsOutgoingWithNoCounterparty() {
        transaction("WDR1000001", wanjikuCurrent, null, "100", TransactionType.WITHDRAWAL);

        TransactionHistoryResponse withdrawal = service.getRecent(WANJIKU, "1000004821", 5).get(0);

        assertThat(withdrawal.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(withdrawal.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(withdrawal.counterpartyLast4()).isNull();
    }

    @Test
    void findByReferenceReturnsOwnTransactionFromTheCustomersSide() {
        transaction("TRN1000001", otienoCurrent, wanjikuCurrent, "75", TransactionType.TRANSFER);

        TransactionHistoryResponse received = service.findByReference(WANJIKU, "TRN1000001");
        TransactionHistoryResponse sent = service.findByReference(OTIENO, "TRN1000001");

        assertThat(received.direction()).isEqualTo(TransactionDirection.IN);
        assertThat(received.counterpartyLast4()).isEqualTo("7777");
        assertThat(sent.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(sent.counterpartyLast4()).isEqualTo("4821");
    }

    @Test
    void findByReferenceShowsTransferBetweenOwnAccountsAsOutgoing() {
        transaction("TRN1000001", wanjikuCurrent, wanjikuSavings, "10", TransactionType.TRANSFER);

        TransactionHistoryResponse own = service.findByReference(WANJIKU, "TRN1000001");

        assertThat(own.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(own.counterpartyLast4()).isEqualTo("9310");
    }

    @Test
    void findByReferenceGivesTheSameErrorForAnotherCustomersAndUnknownReferences() {
        transaction("DEP1000001", null, otienoCurrent, "500", TransactionType.DEPOSIT);

        assertThatThrownBy(() -> service.findByReference(WANJIKU, "DEP1000001"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transaction DEP1000001 not found");
        assertThatThrownBy(() -> service.findByReference(WANJIKU, "DEP9999999"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessage("Transaction DEP9999999 not found");
    }

    private Account account(String number, User owner) {
        return em.persist(new Account(number, BigDecimal.ZERO, AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", owner, "pin-hash"));
    }

    private void transaction(String reference, Account from, Account to, String amount, TransactionType type) {
        // createdAt comes from @PrePersist; wait for the clock to move so "newest first" is deterministic
        Instant before = Instant.now();
        while (!Instant.now().isAfter(before)) {
            Thread.onSpinWait();
        }
        em.persist(new Transaction(reference, from, to, new BigDecimal(amount), type, TransactionStatus.SUCCESS));
        em.flush();
    }
}
