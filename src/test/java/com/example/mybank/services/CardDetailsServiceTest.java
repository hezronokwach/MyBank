package com.example.mybank.services;

import com.example.mybank.dto.responses.CardDetailsResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.CardDetailView;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardRevealOutcome;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardPinLockedException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.repository.CardDetailViewRepository;
import com.example.mybank.services.impl.CardDetailsServiceImpl;
import com.example.mybank.services.impl.CardPinChecker;
import com.example.mybank.util.CardEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Showing a card's full details only after the PIN (issue #45).
 *
 * Wanjiku's card is 9991234567890123, CVV 042, expiring 11/2029. Her PIN is 4721.
 */
@DataJpaTest(properties = "app.cards.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import({CardDetailsServiceImpl.class, CardPinChecker.class, CardEncryptor.class, CardDetailsServiceTest.Beans.class})
@ExtendWith(OutputCaptureExtension.class)
class CardDetailsServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String PIN = "4721";
    private static final String WRONG_PIN = "8362";
    private static final String CARD_NUMBER = "9991234567890123";
    private static final String CVV = "042";

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
    private CardDetailsService service;

    @Autowired
    private CardDetailViewRepository detailViews;

    @Autowired
    private CardEncryptor cardEncryptor;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User wanjiku;
    private Account current;
    private UUID cardId;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        current = em.persist(new Account("1000004821", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode(PIN)));
        cardId = issuedCard(wanjiku, current).getId();
        em.flush();
    }

    @Test
    void theRightPinShowsTheDetailsAndIsRecorded() {
        CardDetailsResponse details = service.revealDetails(WANJIKU, cardId, PIN);

        assertThat(details.cardNumber()).isEqualTo(CARD_NUMBER);
        assertThat(details.cvv()).isEqualTo(CVV);
        assertThat(details.expiryMonth()).isEqualTo(11);
        assertThat(details.expiryYear()).isEqualTo(2029);
        assertThat(details.last4()).isEqualTo("0123");
        assertThat(outcomes()).containsExactly(CardRevealOutcome.SHOWN);
        // Even printed by mistake, the response hides the details
        assertThat(details.toString()).doesNotContain(CARD_NUMBER).doesNotContain(CVV);
    }

    @Test
    void aWrongPinShowsNothingAndIsRecorded() {
        assertThatThrownBy(() -> service.revealDetails(WANJIKU, cardId, WRONG_PIN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid PIN");

        assertThat(outcomes()).containsExactly(CardRevealOutcome.WRONG_PIN);
        em.flush();
        em.clear();
        assertThat(em.find(Card.class, cardId).getFailedPinAttempts()).isEqualTo(1);
    }

    @Test
    void threeWrongPinsLockTheDetailsEvenForTheRightPin() {
        tryWrongPin();
        tryWrongPin();
        assertThatThrownBy(() -> service.revealDetails(WANJIKU, cardId, WRONG_PIN))
                .isInstanceOf(CardPinLockedException.class);

        assertThatThrownBy(() -> service.revealDetails(WANJIKU, cardId, PIN))
                .isInstanceOf(CardPinLockedException.class)
                .hasMessage("Too many wrong PINs. Try again in 15 minutes.");

        // Two wrong PINs, then two refused because locked (the 3rd wrong PIN and the right one)
        assertThat(outcomes()).containsExactlyInAnyOrder(CardRevealOutcome.LOCKED, CardRevealOutcome.LOCKED,
                CardRevealOutcome.WRONG_PIN, CardRevealOutcome.WRONG_PIN);
    }

    @Test
    void aCardThatIsNotIssuedYetHasNoDetails() {
        Card pending = new Card(wanjiku, current, "KES", new BigDecimal("300"));
        UUID pendingId = em.persist(pending).getId();
        em.flush();

        assertThatThrownBy(() -> service.revealDetails(WANJIKU, pendingId, PIN))
                .isInstanceOf(CardStatusConflictException.class)
                .hasMessage("This card has not been issued yet");
    }

    @Test
    void someoneElsesCardLooksLikeItDoesNotExist() {
        User otieno = em.persist(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        Account theirs = em.persist(new Account("1000007777", new BigDecimal("10"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", otieno, passwordEncoder.encode(PIN)));
        UUID theirCard = issuedCard(otieno, theirs).getId();
        em.flush();

        // Even with the right PIN for that account
        assertThatThrownBy(() -> service.revealDetails(WANJIKU, theirCard, PIN))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void theLogsNeverContainTheCardNumberTheCvvOrThePin(CapturedOutput output) {
        service.revealDetails(WANJIKU, cardId, PIN);
        tryWrongPin();

        String everythingPrinted = output.getAll();
        assertThat(everythingPrinted).doesNotContain(CARD_NUMBER);
        assertThat(everythingPrinted).doesNotContain("cvv=" + CVV).doesNotContain("\"" + CVV + "\"");
        assertThat(everythingPrinted).doesNotContain(PIN).doesNotContain(WRONG_PIN);
    }

    // ---- helpers

    private void tryWrongPin() {
        try {
            service.revealDetails(WANJIKU, cardId, WRONG_PIN);
        } catch (UnauthorizedException | CardPinLockedException expected) {
            // a wrong PIN is supposed to fail
        }
    }

    private Card issuedCard(User owner, Account linked) {
        Card card = new Card(owner, linked, "KES", new BigDecimal("300"));
        card.setStatus(CardStatus.ACTIVE);
        card.setCardNumberEncrypted(cardEncryptor.encrypt(CARD_NUMBER));
        card.setCvvEncrypted(cardEncryptor.encrypt(CVV));
        card.setLast4("0123");
        card.setExpiryMonth(11);
        card.setExpiryYear(2029);
        return em.persist(card);
    }

    private List<CardRevealOutcome> outcomes() {
        em.flush();
        List<CardRevealOutcome> result = new ArrayList<>();
        for (CardDetailView view : detailViews.findByCardIdOrderByCreatedAtDesc(cardId)) {
            result.add(view.getOutcome());
        }
        return result;
    }
}
