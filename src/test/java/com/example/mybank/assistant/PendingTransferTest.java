package com.example.mybank.assistant;

import com.example.mybank.assistant.dto.PendingActionView;
import com.example.mybank.assistant.entity.PendingAction;
import com.example.mybank.assistant.entity.PendingActionStatus;
import com.example.mybank.assistant.entity.PendingActionType;
import com.example.mybank.assistant.repository.PendingActionRepository;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.PendingActionConflictException;
import com.example.mybank.exceptions.PinRequiredException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.services.TransferService;
import com.example.mybank.services.impl.AccountServiceImpl;
import com.example.mybank.services.impl.TransferServiceImpl;
import com.example.mybank.util.AccountNumberGenerator;
import com.example.mybank.util.ReferenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transfers prepared by the assistant (issue #20).
 *
 * Wanjiku sends money from her current account ...4821 (KES 1,500) to Otieno's ...7777 (KES 0).
 * Nothing moves until she types her PIN on the confirm card. Her real PIN is 4721.
 */
@DataJpaTest
@Import({
        PendingActionService.class, AccountServiceImpl.class, AccountMapper.class, AccountNumberGenerator.class,
        TransferServiceImpl.class, TransactionMapper.class, ReferenceGenerator.class,
        AssistantMetrics.class, TestMetricsConfig.class,
        PendingTransferTest.Beans.class
})
@ExtendWith(OutputCaptureExtension.class)
class PendingTransferTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String RIGHT_PIN = "4721";
    private static final String WRONG_PIN = "9386";

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
    private PendingActionService service;

    @Autowired
    private TransferService transferService;

    @Autowired
    private PendingActionRepository pendingActions;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User wanjiku;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User otieno = em.persist(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        em.persist(new Account("1000004821", new BigDecimal("1500.00"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode(RIGHT_PIN)));
        em.persist(new Account("1000007777", new BigDecimal("0.00"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", otieno, passwordEncoder.encode("1111")));
        em.flush();
    }

    @Test
    void preparingATransferMovesNoMoney() {
        PendingAction action = prepare("500");

        assertThat(action.getType()).isEqualTo(PendingActionType.TRANSFER);
        assertThat(action.getStatus()).isEqualTo(PendingActionStatus.PENDING);
        assertThat(action.getSummary()).isEqualTo("Send KES 500.00 from ...4821 to ...7777");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(balanceOf("1000007777")).isEqualByComparingTo("0");
    }

    @Test
    void theRightPinSendsTheMoney() {
        PendingAction action = prepare("500");

        PendingActionView confirmed = service.confirm(WANJIKU, action.getId(), RIGHT_PIN);

        assertThat(confirmed.status()).isEqualTo(PendingActionStatus.CONFIRMED);
        assertThat(confirmed.result()).startsWith("Sent. Reference TRN");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(balanceOf("1000007777")).isEqualByComparingTo("500");
    }

    @Test
    void aWrongPinFailsAndThePinIsNeverStoredOrLogged(CapturedOutput output) {
        PendingAction action = prepare("500");

        PendingActionView result = service.confirm(WANJIKU, action.getId(), WRONG_PIN);

        assertThat(result.status()).isEqualTo(PendingActionStatus.FAILED);
        assertThat(result.result()).isEqualTo("Wrong PIN. Nothing was sent. Ask the assistant again if you still want to send it.");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
        assertThat(balanceOf("1000007777")).isEqualByComparingTo("0");

        // Not in any column of the saved action
        PendingAction saved = pendingActions.findById(action.getId()).orElseThrow();
        assertThat(saved.getSummary()).doesNotContain(WRONG_PIN);
        assertThat(saved.getResult()).doesNotContain(WRONG_PIN);
        assertThat(saved.getReason()).isNull();
        // Not in the logs
        assertThat(output.getAll()).doesNotContain(WRONG_PIN);

        // The card can't be tried again with another PIN
        assertThatThrownBy(() -> service.confirm(WANJIKU, action.getId(), RIGHT_PIN))
                .isInstanceOf(PendingActionConflictException.class)
                .hasMessage("This request was already failed");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
    }

    @Test
    void confirmingTwiceSendsTheMoneyOnlyOnce() {
        PendingAction action = prepare("500");
        service.confirm(WANJIKU, action.getId(), RIGHT_PIN);

        assertThatThrownBy(() -> service.confirm(WANJIKU, action.getId(), RIGHT_PIN))
                .isInstanceOf(PendingActionConflictException.class)
                .hasMessage("This request was already confirmed");

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(balanceOf("1000007777")).isEqualByComparingTo("500");
        assertThat(transferCount()).isEqualTo(1);
    }

    @Test
    void theActionIdIsTheIdempotencyKeySoTheTransferCanOnlyHappenOnce() {
        // Even if the transfer were somehow run twice with the card's key, the money moves once
        PendingAction action = prepare("500");
        String key = "assistant-" + action.getId();

        transferService.transfer(WANJIKU, "1000004821", "1000007777", new BigDecimal("500"), key, RIGHT_PIN);
        transferService.transfer(WANJIKU, "1000004821", "1000007777", new BigDecimal("500"), key, RIGHT_PIN);

        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1000");
        assertThat(transferCount()).isEqualTo(1);
    }

    @Test
    void confirmingWithoutAPinChangesNothing() {
        PendingAction action = prepare("500");

        assertThatThrownBy(() -> service.confirm(WANJIKU, action.getId(), ""))
                .isInstanceOf(PinRequiredException.class)
                .hasMessage("Enter your PIN to confirm this transfer");

        // Still waiting, so she can type the PIN and press Confirm again
        assertThat(pendingActions.findById(action.getId()).orElseThrow().getStatus()).isEqualTo(PendingActionStatus.PENDING);
        assertThat(service.confirm(WANJIKU, action.getId(), RIGHT_PIN).status()).isEqualTo(PendingActionStatus.CONFIRMED);
    }

    @Test
    void notEnoughMoneyFailsWithTheReason() {
        PendingAction action = prepare("5000");

        PendingActionView result = service.confirm(WANJIKU, action.getId(), RIGHT_PIN);

        assertThat(result.status()).isEqualTo(PendingActionStatus.FAILED);
        assertThat(result.result()).isEqualTo("Insufficient balance");
        assertThat(balanceOf("1000004821")).isEqualByComparingTo("1500");
    }

    private PendingAction prepare(String amount) {
        return service.createTransfer(wanjiku.getId(), null, "1000004821", "1000007777", new BigDecimal(amount), "KES");
    }

    private BigDecimal balanceOf(String accountNumber) {
        em.flush();
        em.clear();
        return em.getEntityManager()
                .createQuery("select a.balance from Account a where a.accountNumber = :n", BigDecimal.class)
                .setParameter("n", accountNumber)
                .getSingleResult();
    }

    private long transferCount() {
        return em.getEntityManager()
                .createQuery("select count(t) from Transaction t where t.type = com.example.mybank.enums.TransactionType.TRANSFER", Long.class)
                .getSingleResult();
    }
}
