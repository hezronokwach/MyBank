package com.example.mybank.services;

import com.example.mybank.dto.responses.CardResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.AccountStatusException;
import com.example.mybank.exceptions.CardAlreadyExistsException;
import com.example.mybank.exceptions.CurrencyMatchException;
import com.example.mybank.exceptions.InsufficientBalanceException;
import com.example.mybank.exceptions.TransactionNotAllowedException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.services.impl.CardServiceImpl;
import com.example.mybank.util.ReferenceGenerator;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applying for a virtual card (issue #42). The fee is KES 300 (the default).
 * Wanjiku's PIN is 4721 on all her accounts.
 */
@DataJpaTest
@Import({CardServiceImpl.class, ReferenceGenerator.class, CardServiceTest.Beans.class})
class CardServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String PIN = "4721";

    @TestConfiguration
    static class Beans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CardService cardService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User wanjiku;
    private User otieno;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        otieno = em.persist(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        account("1000004821", wanjiku, AccountType.CURRENT, AccountStatus.ACTIVE, "KES", "1500.00");
        em.flush();
    }

    @Test
    void applyingTakesTheFeeOnceAndWaitsForAnAdmin() {
        CardResponse card = cardService.applyForCard(WANJIKU, "1000004821", PIN);

        assertThat(card.status()).isEqualTo(CardStatus.PENDING_APPROVAL);
        assertThat(card.feePaid()).isEqualByComparingTo("300");
        assertThat(card.balance()).isEqualByComparingTo("0");
        assertThat(card.currency()).isEqualTo("KES");
        assertThat(card.linkedAccount()).isEqualTo("...4821");
        // No card number until an admin approves it
        assertThat(card.last4()).isNull();
        assertThat(card.expiryYear()).isNull();

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1200");
        assertThat(cardFeeCount()).isEqualTo(1);
    }

    @Test
    void aSavingsAccountCanPayToo() {
        account("1000009310", wanjiku, AccountType.SAVINGS, AccountStatus.ACTIVE, "KES", "5000.00");

        CardResponse card = cardService.applyForCard(WANJIKU, "1000009310", PIN);

        assertThat(card.linkedAccount()).isEqualTo("...9310");
        assertThat(balanceOf("1000009310")).isEqualByComparingTo("4700");
    }

    @Test
    void aFixedAccountCannotPay() {
        account("1000005555", wanjiku, AccountType.FIXED_01, AccountStatus.ACTIVE, "KES", "50000.00");

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000005555", PIN))
                .isInstanceOf(TransactionNotAllowedException.class)
                .hasMessage("Only a current or savings account can pay for a card");

        assertNothingHappened("1000005555", "50000");
    }

    @Test
    void anAccountThatIsNotActiveCannotPay() {
        account("1000006666", wanjiku, AccountType.SAVINGS, AccountStatus.PENDING_VERIFICATION, "KES", "2000.00");

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000006666", PIN))
                .isInstanceOf(AccountStatusException.class);

        assertNothingHappened("1000006666", "2000");
    }

    @Test
    void aWrongPinIsRefused() {
        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000004821", "9999"))
                .isInstanceOf(UnauthorizedException.class);

        assertNothingHappened("1000004821", "1500");
    }

    @Test
    void notEnoughMoneyIsRefused() {
        // Exactly the fee is not enough: the balance must stay above 0
        account("1000007000", wanjiku, AccountType.SAVINGS, AccountStatus.ACTIVE, "KES", "300.00");

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000007000", PIN))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("Insufficient balance for the card fee of KES 300");

        assertNothingHappened("1000007000", "300");
    }

    @Test
    void anAccountInAnotherCurrencyIsRefused() {
        account("1000008000", wanjiku, AccountType.SAVINGS, AccountStatus.ACTIVE, "USD", "900.00");

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000008000", PIN))
                .isInstanceOf(CurrencyMatchException.class);

        assertNothingHappened("1000008000", "900");
    }

    @Test
    void aSecondCardIsRefusedAndOnlyOneFeeIsTaken() {
        cardService.applyForCard(WANJIKU, "1000004821", PIN);

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000004821", PIN))
                .isInstanceOf(CardAlreadyExistsException.class)
                .hasMessage("You already have a card");

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1200");
        assertThat(cardRepository.count()).isEqualTo(1);
        assertThat(cardFeeCount()).isEqualTo(1);
    }

    @Test
    void someoneElsesAccountLooksLikeItDoesNotExist() {
        account("1000007777", otieno, AccountType.CURRENT, AccountStatus.ACTIVE, "KES", "99999.00");

        assertThatThrownBy(() -> cardService.applyForCard(WANJIKU, "1000007777", PIN))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found");

        assertNothingHappened("1000007777", "99999");
    }

    @Test
    void myCardsListsOnlyMyOwnCards() {
        account("1000007777", otieno, AccountType.CURRENT, AccountStatus.ACTIVE, "KES", "99999.00");
        cardService.applyForCard("otieno@test.local", "1000007777", PIN);

        assertThat(cardService.getMyCards(WANJIKU)).isEmpty();

        cardService.applyForCard(WANJIKU, "1000004821", PIN);
        List<CardResponse> mine = cardService.getMyCards(WANJIKU);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).linkedAccount()).isEqualTo("...4821");
    }

    // ---- helpers

    private void account(String number, User owner, AccountType type, AccountStatus status, String currency, String balance) {
        em.persist(new Account(number, new BigDecimal(balance), status, AccountTier.TIER_2_VERIFIED,
                type, currency, owner, passwordEncoder.encode(PIN)));
    }

    private void assertNothingHappened(String accountNumber, String balance) {
        assertThat(balanceOf(accountNumber)).isEqualByComparingTo(balance);
        assertThat(cardRepository.count()).isZero();
        assertThat(cardFeeCount()).isZero();
    }

    private BigDecimal balanceOf(String accountNumber) {
        em.flush();
        em.clear();
        return em.getEntityManager()
                .createQuery("select a.balance from Account a where a.accountNumber = :n", BigDecimal.class)
                .setParameter("n", accountNumber)
                .getSingleResult();
    }

    private long cardFeeCount() {
        return em.getEntityManager()
                .createQuery("select count(t) from Transaction t where t.type = :type", Long.class)
                .setParameter("type", TransactionType.CARD_FEE)
                .getSingleResult();
    }
}
