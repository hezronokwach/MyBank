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
import com.example.mybank.exceptions.PendingActionNotFoundException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.services.impl.TransferServiceImpl;
import com.example.mybank.util.ReferenceGenerator;
import com.example.mybank.services.impl.AccountServiceImpl;
import com.example.mybank.util.AccountNumberGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        PendingActionService.class, AccountServiceImpl.class, AccountMapper.class, AccountNumberGenerator.class,
        AssistantMetrics.class, TestMetricsConfig.class,
        TransferServiceImpl.class, TransactionMapper.class, ReferenceGenerator.class,
        PendingActionServiceTest.Beans.class
})
class PendingActionServiceTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String OTIENO = "otieno@test.local";

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
    private PendingActionRepository pendingActions;

    @Autowired
    private MeterRegistry meterRegistry;

    private User wanjiku;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        em.persist(new User(OTIENO, "hash", "Otieno", "O", UserRole.ROLE_USER));
        em.persist(new Account("1000004821", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, "pin-hash"));
        em.flush();
    }

    @Test
    void creatingTheRequestDoesNotFreezeTheAccount() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");

        assertThat(action.getStatus()).isEqualTo(PendingActionStatus.PENDING);
        assertThat(action.getType()).isEqualTo(PendingActionType.FREEZE_ACCOUNT);
        assertThat(action.getSummary()).isEqualTo("Freeze account ...4821");
        assertThat(action.getExpiresAt()).isAfter(Instant.now().plusSeconds(4 * 60));
        assertThat(accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void confirmFreezesTheAccount() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");

        PendingActionView confirmed = service.confirm(WANJIKU, action.getId());

        assertThat(confirmed.status()).isEqualTo(PendingActionStatus.CONFIRMED);
        assertThat(confirmed.result()).isEqualTo("Account ...4821 is frozen. To unfreeze it, contact support.");
        assertThat(accountStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void creatingAndConfirmingAreCounted() {
        double pendingBefore = count("assistant.actions", "type", "FREEZE_ACCOUNT", "status", "PENDING");
        double confirmedBefore = count("assistant.actions", "type", "FREEZE_ACCOUNT", "status", "CONFIRMED");

        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");
        service.confirm(WANJIKU, action.getId());

        assertThat(count("assistant.actions", "type", "FREEZE_ACCOUNT", "status", "PENDING")).isEqualTo(pendingBefore + 1);
        assertThat(count("assistant.actions", "type", "FREEZE_ACCOUNT", "status", "CONFIRMED")).isEqualTo(confirmedBefore + 1);
    }

    @Test
    void confirmingTwiceFailsTheSecondTime() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");
        service.confirm(WANJIKU, action.getId());

        assertThatThrownBy(() -> service.confirm(WANJIKU, action.getId()))
                .isInstanceOf(PendingActionConflictException.class)
                .hasMessage("This request was already confirmed");
    }

    @Test
    void confirmingAfterExpiryFailsAndMarksItExpired() {
        PendingAction old = pendingActions.save(new PendingAction(wanjiku.getId(), null,
                PendingActionType.FREEZE_ACCOUNT, "1000004821", "lost phone", "Freeze account ...4821",
                Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.confirm(WANJIKU, old.getId()))
                .isInstanceOf(PendingActionConflictException.class)
                .hasMessage("This request has expired. Ask the assistant again.");

        assertThat(pendingActions.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(PendingActionStatus.EXPIRED);
        assertThat(accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anotherCustomersActionIsNotFound() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");

        assertThatThrownBy(() -> service.confirm(OTIENO, action.getId()))
                .isInstanceOf(PendingActionNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(OTIENO, action.getId()))
                .isInstanceOf(PendingActionNotFoundException.class);
        assertThatThrownBy(() -> service.confirm(WANJIKU, UUID.randomUUID()))
                .isInstanceOf(PendingActionNotFoundException.class);

        assertThat(accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void cancelChangesNothingAndCannotBeConfirmedAfterwards() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");

        PendingActionView cancelled = service.cancel(WANJIKU, action.getId());

        assertThat(cancelled.status()).isEqualTo(PendingActionStatus.CANCELLED);
        assertThat(accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.confirm(WANJIKU, action.getId()))
                .isInstanceOf(PendingActionConflictException.class)
                .hasMessage("This request was already cancelled");
    }

    @Test
    void confirmFailsCleanlyWhenTheAccountIsNoLongerActive() {
        PendingAction action = service.createFreeze(wanjiku.getId(), null, "1000004821", "lost phone");
        // Someone froze the account another way in the meantime
        PendingAction other = service.createFreeze(wanjiku.getId(), null, "1000004821", "stolen card");
        service.confirm(WANJIKU, other.getId());

        PendingActionView result = service.confirm(WANJIKU, action.getId());

        assertThat(result.status()).isEqualTo(PendingActionStatus.FAILED);
        assertThat(result.result()).isEqualTo("Only active accounts can be frozen");
    }

    @Test
    void expiryJobExpiresOnlyOldPendingActions() {
        PendingAction old = pendingActions.save(new PendingAction(wanjiku.getId(), null,
                PendingActionType.FREEZE_ACCOUNT, "1000004821", null, "Freeze account ...4821",
                Instant.now().minusSeconds(60)));
        PendingAction fresh = service.createFreeze(wanjiku.getId(), null, "1000004821", null);

        service.expireOldActions();

        assertThat(pendingActions.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(PendingActionStatus.EXPIRED);
        assertThat(pendingActions.findById(fresh.getId()).orElseThrow().getStatus()).isEqualTo(PendingActionStatus.PENDING);
    }

    private AccountStatus accountStatus() {
        em.flush();
        em.clear();
        return em.getEntityManager()
                .createQuery("select a.status from Account a where a.accountNumber = :n", AccountStatus.class)
                .setParameter("n", "1000004821")
                .getSingleResult();
    }

    // The value of a counter, or 0 if nothing was counted yet
    private double count(String name, String tagName, String tagValue, String tagName2, String tagValue2) {
        Counter counter = meterRegistry.find(name).tag(tagName, tagValue).tag(tagName2, tagValue2).counter();
        if (counter == null) {
            return 0;
        }
        return counter.count();
    }
}
