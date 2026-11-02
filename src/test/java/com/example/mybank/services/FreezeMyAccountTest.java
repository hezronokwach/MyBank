package com.example.mybank.services;

import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.AccountStatusException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.services.impl.AccountServiceImpl;
import com.example.mybank.services.impl.TransactionServiceImpl;
import com.example.mybank.services.impl.TransferServiceImpl;
import com.example.mybank.util.AccountNumberGenerator;
import com.example.mybank.util.ReferenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        AccountServiceImpl.class, TransactionServiceImpl.class, TransferServiceImpl.class,
        AccountMapper.class, TransactionMapper.class, AccountNumberGenerator.class, ReferenceGenerator.class,
        FreezeMyAccountTest.Beans.class
})
class FreezeMyAccountTest {

    private static final String WANJIKU = "wanjiku@test.local";
    private static final String OTIENO = "otieno@test.local";
    private static final String PIN = "1234";

    @TestConfiguration
    static class Beans {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4); // low cost keeps the tests fast
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    private User wanjiku;
    private User otieno;

    @BeforeEach
    void seed() {
        wanjiku = em.persist(new User(WANJIKU, "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        otieno = em.persist(new User(OTIENO, "hash", "Otieno", "O", UserRole.ROLE_USER));
    }

    @Test
    void freezesOwnActiveAccount() {
        account("1000004821", wanjiku, AccountStatus.ACTIVE);

        AccountResponse response = accountService.freezeMyAccount(WANJIKU, "1000004821", "lost phone");

        assertThat(response.status()).isEqualTo(AccountStatus.FROZEN);
        assertThat(statusInDatabase("1000004821")).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void rejectsAnotherCustomersAccount() {
        account("1000007777", otieno, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> accountService.freezeMyAccount(WANJIKU, "1000007777", "lost phone"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(statusInDatabase("1000007777")).isEqualTo(AccountStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"FROZEN", "PENDING_VERIFICATION", "CLOSED"})
    void rejectsAccountsThatAreNotActive(AccountStatus status) {
        account("1000004821", wanjiku, status);

        assertThatThrownBy(() -> accountService.freezeMyAccount(WANJIKU, "1000004821", "lost phone"))
                .isInstanceOf(AccountStatusException.class)
                .hasMessage("Only active accounts can be frozen");
        assertThat(statusInDatabase("1000004821")).isEqualTo(status);
    }

    @Test
    void frozenAccountRejectsDepositsAndWithdrawals() {
        account("1000004821", wanjiku, AccountStatus.ACTIVE);
        accountService.freezeMyAccount(WANJIKU, "1000004821", "lost phone");

        assertThatThrownBy(() -> transactionService.deposit(WANJIKU, "1000004821", new BigDecimal("100"), PIN))
                .isInstanceOf(AccountStatusException.class);
        assertThatThrownBy(() -> transactionService.withdraw(WANJIKU, "1000004821", new BigDecimal("100"), PIN))
                .isInstanceOf(AccountStatusException.class);
    }

    @Test
    void frozenAccountCanNeitherSendNorReceiveTransfers() {
        account("1000004821", wanjiku, AccountStatus.ACTIVE);
        account("1000007777", otieno, AccountStatus.ACTIVE);
        accountService.freezeMyAccount(WANJIKU, "1000004821", "lost phone");

        assertThatThrownBy(() -> transferService.transfer(WANJIKU, "1000004821", "1000007777",
                new BigDecimal("100"), "key-out", PIN))
                .isInstanceOf(AccountStatusException.class);
        assertThatThrownBy(() -> transferService.transfer(OTIENO, "1000007777", "1000004821",
                new BigDecimal("100"), "key-in", PIN))
                .isInstanceOf(AccountStatusException.class);
    }

    private void account(String number, User owner, AccountStatus status) {
        em.persist(new Account(number, new BigDecimal("1000"), status, AccountTier.TIER_2_VERIFIED,
                AccountType.CURRENT, "KES", owner, passwordEncoder.encode(PIN)));
        em.flush();
    }

    private AccountStatus statusInDatabase(String number) {
        em.flush();
        em.clear();
        return em.getEntityManager()
                .createQuery("select a.status from Account a where a.accountNumber = :n", AccountStatus.class)
                .setParameter("n", number)
                .getSingleResult();
    }
}
