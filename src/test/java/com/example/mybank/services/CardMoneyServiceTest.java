package com.example.mybank.services;

import com.example.mybank.dto.responses.CardMoveResponse;
import com.example.mybank.dto.responses.CardTransactionResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardPinLockedException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.exceptions.InsufficientBalanceException;
import com.example.mybank.exceptions.TransactionNotAllowedException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.services.impl.CardMoneyServiceImpl;
import com.example.mybank.services.impl.CardPinChecker;
import com.example.mybank.util.ReferenceGenerator;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Moving money between an account and the card (issue #44).
 *
 * Wanjiku: current ...4821 (KES 1,500), savings ...9310 (KES 5,000), fixed ...5555 (KES 50,000), PIN 4721.
 * Her card is linked to ...4821 and starts empty.
 */
@DataJpaTest
@Import({CardMoneyServiceImpl.class, CardPinChecker.class, ReferenceGenerator.class, CardMoneyServiceTest.Beans.class})
class CardMoneyServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String PIN = "4721";
    private static final String WRONG_PIN = "1111";

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
    private CardMoneyService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User wanjiku;
    private User otieno;
    private UUID cardId;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        otieno = em.persist(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        Account current = account("1000004821", wanjiku, AccountType.CURRENT, "1500.00");
        account("1000009310", wanjiku, AccountType.SAVINGS, "5000.00");
        account("1000005555", wanjiku, AccountType.FIXED_01, "50000.00");
        cardId = activeCard(wanjiku, current).getId();
        em.flush();
    }

    @Test
    void topUpMovesMoneyOntoTheCard() {
        CardMoveResponse move = service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN);

        assertThat(move.type()).isEqualTo(TransactionType.CARD_TOPUP);
        assertThat(move.reference()).startsWith("CTU");
        assertThat(move.account()).isEqualTo("...4821");
        assertThat(move.cardBalance()).isEqualByComparingTo("500");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(cardBalance()).isEqualByComparingTo("500");
    }

    @Test
    void withdrawMovesMoneyBackAndTheCardCanBeEmptied() {
        service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN);

        CardMoveResponse back = service.withdraw(WANJIKU, cardId, "1000009310", new BigDecimal("200"), key(), PIN);
        assertThat(back.type()).isEqualTo(TransactionType.CARD_WITHDRAWAL);
        assertThat(back.reference()).startsWith("CWD");
        assertThat(back.cardBalance()).isEqualByComparingTo("300");
        assertThat(balanceOf("1000009310")).isEqualByComparingTo("5200");

        service.withdraw(WANJIKU, cardId, "1000004821", new BigDecimal("300"), key(), PIN);
        assertThat(cardBalance()).isEqualByComparingTo("0");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1300");
    }

    @Test
    void aWrongPinMovesNothingAndIsCounted() {
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), WRONG_PIN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid PIN");

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(cardBalance()).isEqualByComparingTo("0");
        assertThat(reloadCard().getFailedPinAttempts()).isEqualTo(1);
    }

    @Test
    void threeWrongPinsLockTheCardEvenForTheRightPin() {
        tryWrongPin();
        tryWrongPin();
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), WRONG_PIN))
                .isInstanceOf(CardPinLockedException.class)
                .hasMessage("Too many wrong PINs. Try again in 15 minutes.");

        // Locked: even the right PIN is refused, and nothing moves
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN))
                .isInstanceOf(CardPinLockedException.class);
        assertThatThrownBy(() -> service.withdraw(WANJIKU, cardId, "1000004821", new BigDecimal("1"), key(), PIN))
                .isInstanceOf(CardPinLockedException.class);
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(reloadCard().getPinLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));
    }

    @Test
    void theLockEndsAfter15Minutes() {
        tryWrongPin();
        tryWrongPin();
        tryWrongPin();
        // Pretend the 15 minutes have passed
        Card card = reloadCard();
        card.setPinLockedUntil(Instant.now().minusSeconds(1));
        em.flush();

        service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN);

        assertThat(cardBalance()).isEqualByComparingTo("500");
        assertThat(reloadCard().getPinLockedUntil()).isNull();
        assertThat(reloadCard().getFailedPinAttempts()).isZero();
    }

    @Test
    void theRightPinStartsTheCountAgain() {
        tryWrongPin();
        tryWrongPin();
        service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("10"), key(), PIN);
        tryWrongPin();

        // Only 1 wrong PIN since the right one, so not locked
        assertThat(reloadCard().getFailedPinAttempts()).isEqualTo(1);
        assertThat(reloadCard().getPinLockedUntil()).isNull();
    }

    @Test
    void aFixedAccountCannotBeUsedEitherWay() {
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000005555", new BigDecimal("500"), key(), PIN))
                .isInstanceOf(TransactionNotAllowedException.class)
                .hasMessage("Only a current or savings account can be used with a card");

        service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN);
        assertThatThrownBy(() -> service.withdraw(WANJIKU, cardId, "1000005555", new BigDecimal("100"), key(), PIN))
                .isInstanceOf(TransactionNotAllowedException.class);

        assertThat(balanceOf("1000005555")).isEqualByComparingTo("50000");
        assertThat(cardBalance()).isEqualByComparingTo("500");
    }

    @Test
    void aCardThatIsNotActiveCannotBeUsed() {
        Card card = reloadCard();
        card.setStatus(CardStatus.FROZEN);
        em.flush();

        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN))
                .isInstanceOf(CardStatusConflictException.class)
                .hasMessage("This card is not active");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
    }

    @Test
    void someoneElsesCardOrAccountLooksLikeItDoesNotExist() {
        Account theirs = account("1000007777", otieno, AccountType.CURRENT, "9000.00");
        UUID theirCard = activeCard(otieno, theirs).getId();
        em.flush();

        assertThatThrownBy(() -> service.topUp(WANJIKU, theirCard, "1000004821", new BigDecimal("500"), key(), PIN))
                .isInstanceOf(CardNotFoundException.class);
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000007777", new BigDecimal("500"), key(), PIN))
                .isInstanceOf(AccountNotFoundException.class);

        assertThat(balanceOf("1000007777")).isEqualByComparingTo("9000");
        assertThat(cardBalance()).isEqualByComparingTo("0");
    }

    @Test
    void notEnoughMoneyIsRefusedBothWays() {
        // The account must keep more than 0
        assertThatThrownBy(() -> service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("1500"), key(), PIN))
                .isInstanceOf(InsufficientBalanceException.class);
        // The card is empty
        assertThatThrownBy(() -> service.withdraw(WANJIKU, cardId, "1000004821", new BigDecimal("1"), key(), PIN))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("Not enough money on the card");

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
    }

    @Test
    void theSameKeyTwiceMovesTheMoneyOnce() {
        String sameKey = key();

        CardMoveResponse first = service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), sameKey, PIN);
        CardMoveResponse second = service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), sameKey, PIN);

        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(cardBalance()).isEqualByComparingTo("500");
        assertThat(countOf(TransactionType.CARD_TOPUP)).isEqualTo(1);
    }

    @Test
    void theHistoryShowsBothDirectionsNewestFirst() throws InterruptedException {
        service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), PIN);
        Thread.sleep(5); // so the two moves have different times
        service.withdraw(WANJIKU, cardId, "1000009310", new BigDecimal("200"), key(), PIN);

        List<CardTransactionResponse> history = service.getCardTransactions(WANJIKU, cardId, 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).type()).isEqualTo(TransactionType.CARD_WITHDRAWAL);
        assertThat(history.get(0).direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(history.get(0).account()).isEqualTo("...9310");
        assertThat(history.get(1).type()).isEqualTo(TransactionType.CARD_TOPUP);
        assertThat(history.get(1).direction()).isEqualTo(TransactionDirection.IN);
        assertThat(history.get(1).amount()).isEqualByComparingTo("500");
    }

    // ---- helpers

    private void tryWrongPin() {
        try {
            service.topUp(WANJIKU, cardId, "1000004821", new BigDecimal("500"), key(), WRONG_PIN);
        } catch (UnauthorizedException | CardPinLockedException expected) {
            // a wrong PIN is supposed to fail
        }
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private Account account(String number, User owner, AccountType type, String balance) {
        return em.persist(new Account(number, new BigDecimal(balance), AccountStatus.ACTIVE, AccountTier.TIER_2_VERIFIED,
                type, "KES", owner, passwordEncoder.encode(PIN)));
    }

    private Card activeCard(User owner, Account linked) {
        Card card = new Card(owner, linked, "KES", new BigDecimal("300"));
        card.setStatus(CardStatus.ACTIVE);
        card.setLast4("1234");
        return em.persist(card);
    }

    private Card reloadCard() {
        em.flush();
        em.clear();
        return em.find(Card.class, cardId);
    }

    private BigDecimal cardBalance() {
        return reloadCard().getBalance();
    }

    private BigDecimal balanceOf(String accountNumber) {
        em.flush();
        em.clear();
        return em.getEntityManager()
                .createQuery("select a.balance from Account a where a.accountNumber = :n", BigDecimal.class)
                .setParameter("n", accountNumber)
                .getSingleResult();
    }

    private long countOf(TransactionType type) {
        return em.getEntityManager()
                .createQuery("select count(t) from Transaction t where t.type = :type", Long.class)
                .setParameter("type", type)
                .getSingleResult();
    }
}
