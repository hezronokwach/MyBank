package com.example.mybank.services;

import com.example.mybank.dto.responses.AdminCardResponse;
import com.example.mybank.dto.responses.CardResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.services.impl.CardApprovalServiceImpl;
import com.example.mybank.services.impl.CardServiceImpl;
import com.example.mybank.util.CardEncryptor;
import com.example.mybank.util.CardNumberGenerator;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The admin approves or rejects card applications (issue #43).
 * Wanjiku applies with her current account (KES 1,500); the fee is KES 300.
 */
@DataJpaTest(properties = "app.cards.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import({
        CardApprovalServiceImpl.class, CardServiceImpl.class, CardNumberGenerator.class, CardEncryptor.class,
        ReferenceGenerator.class, CardApprovalServiceTest.Beans.class
})
class CardApprovalServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String ADMIN = "boss@test.local";

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
    private CardApprovalService approvalService;

    @Autowired
    private CardService cardService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardEncryptor cardEncryptor;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID cardId;

    @BeforeEach
    void seed() {
        User wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "Kamau", UserRole.ROLE_USER));
        em.persist(new Account("1000004821", new BigDecimal("1500.00"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode("4721")));
        em.flush();
        CardResponse applied = cardService.applyForCard(WANJIKU, "1000004821", "4721");
        cardId = applied.id();
    }

    @Test
    void approvingIssuesTheCardWithAnEncryptedNumber() {
        AdminCardResponse approved = approvalService.approve(ADMIN, cardId);

        assertThat(approved.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(approved.decidedBy()).isEqualTo(ADMIN);
        assertThat(approved.last4()).hasSize(4).containsOnlyDigits();
        LocalDate inThreeYears = LocalDate.now(ZoneId.of("Africa/Nairobi")).plusYears(3);
        assertThat(approved.expiryYear()).isEqualTo(inThreeYears.getYear());
        assertThat(approved.expiryMonth()).isEqualTo(inThreeYears.getMonthValue());

        // What is saved is encrypted; decrypted, it is a valid 16-digit card number ending in last4
        Card saved = reload();
        String number = cardEncryptor.decrypt(saved.getCardNumberEncrypted());
        assertThat(number).hasSize(16).startsWith("999").endsWith(approved.last4());
        assertThat(CardNumberGenerator.passesLuhn(number)).isTrue();
        assertThat(saved.getCardNumberEncrypted()).doesNotContain(number);
        String cvv = cardEncryptor.decrypt(saved.getCvvEncrypted());
        assertThat(cvv).hasSize(3).containsOnlyDigits();
        assertThat(saved.getCvvEncrypted()).isNotEqualTo(cvv);

        // The fee stays taken
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1200");
    }

    @Test
    void aCardCanOnlyBeApprovedOnce() {
        approvalService.approve(ADMIN, cardId);
        String firstNumber = reload().getCardNumberEncrypted();

        assertThatThrownBy(() -> approvalService.approve(ADMIN, cardId))
                .isInstanceOf(CardStatusConflictException.class)
                .hasMessage("This card was already active");

        // The number did not change
        assertThat(reload().getCardNumberEncrypted()).isEqualTo(firstNumber);
    }

    @Test
    void rejectingRefundsTheFeeExactlyOnce() {
        AdminCardResponse rejected = approvalService.reject(ADMIN, cardId, "  Please verify your ID first  ");

        assertThat(rejected.status()).isEqualTo(CardStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Please verify your ID first");
        assertThat(rejected.last4()).isNull();
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(countOf(TransactionType.CARD_FEE_REFUND)).isEqualTo(1);

        assertThatThrownBy(() -> approvalService.reject(ADMIN, cardId, "again"))
                .isInstanceOf(CardStatusConflictException.class)
                .hasMessage("This card was already rejected");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(countOf(TransactionType.CARD_FEE_REFUND)).isEqualTo(1);
    }

    @Test
    void aRejectedCardCannotBeApproved() {
        approvalService.reject(ADMIN, cardId, "No");
        assertThatThrownBy(() -> approvalService.approve(ADMIN, cardId))
                .isInstanceOf(CardStatusConflictException.class);
        assertThat(reload().getCardNumberEncrypted()).isNull();
    }

    @Test
    void anIssuedCardCannotBeRejected() {
        approvalService.approve(ADMIN, cardId);

        assertThatThrownBy(() -> approvalService.reject(ADMIN, cardId, "Changed my mind"))
                .isInstanceOf(CardStatusConflictException.class);
        // No refund for an issued card
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1200");
        assertThat(countOf(TransactionType.CARD_FEE_REFUND)).isZero();
    }

    @Test
    void anUnknownCardIsNotFound() {
        assertThatThrownBy(() -> approvalService.approve(ADMIN, UUID.randomUUID()))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void theListShowsTheCustomerAndFiltersByStatus() {
        AdminCardResponse waiting = approvalService.listCards(CardStatus.PENDING_APPROVAL).get(0);
        assertThat(waiting.customerName()).isEqualTo("Wanjiku Kamau");
        assertThat(waiting.customerEmail()).isEqualTo(WANJIKU);
        assertThat(waiting.linkedAccountNumber()).isEqualTo("1000004821");
        assertThat(waiting.feePaid()).isEqualByComparingTo("300");

        approvalService.approve(ADMIN, cardId);

        assertThat(approvalService.listCards(CardStatus.PENDING_APPROVAL)).isEmpty();
        assertThat(approvalService.listCards(CardStatus.ACTIVE)).hasSize(1);
        assertThat(approvalService.listCards(null)).hasSize(1);
    }

    // ---- helpers

    private Card reload() {
        em.flush();
        em.clear();
        return cardRepository.findById(cardId).orElseThrow();
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
