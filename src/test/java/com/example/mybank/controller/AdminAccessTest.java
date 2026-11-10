package com.example.mybank.controller;

import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #39: the admin approves customers' accounts but never opens, owns or uses a bank account.
 * The customer endpoints answer 403 for an admin; the admin endpoints still work.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-access;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class AdminAccessTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void seed() {
        User admin = userRepository.save(new User("boss@test.local", "hash", "Ada", "Admin", UserRole.ROLE_ADMIN));
        User customer = userRepository.save(new User("wanjiku@test.local", "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        accountRepository.save(new Account("1000004821", new BigDecimal("1000"), AccountStatus.PENDING_VERIFICATION,
                AccountTier.TIER_1_UNVERIFIED, AccountType.CURRENT, "KES", customer, "pin-hash"));
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(customer);
    }

    @Test
    void anAdminCannotOpenAnAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"type\":\"SAVINGS\",\"currency\":\"KES\",\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void anAdminCannotMoveMoney() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/1000004821/deposits").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"amount\":100,\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/accounts/1000004821/withdrawals").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"amount\":100,\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/accounts/transfers").header("Authorization", adminToken)
                        .header("Idempotency-key", UUID.randomUUID().toString())
                        .contentType(JSON).content("{\"fromAccountNumber\":\"1000004821\",\"toAccountNumber\":\"1000007777\",\"amount\":100,\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCannotUseTheCustomerAccountPages() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/1000004821/transactions").header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/accounts/1000004821").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCannotUseTheAssistant() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"message\":\"List every account in the bank\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanStillListAndApproveAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("1000004821"));

        mockMvc.perform(patch("/api/v1/accounts/1000004821/status").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        assertThat(accountRepository.findByAccountNumber("1000004821").orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aCustomerStillUsesTheirAccountsAndCannotApprove() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("1000004821"));

        mockMvc.perform(patch("/api/v1/accounts/1000004821/status").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }
}
