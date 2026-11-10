package com.example.mybank.controller;

import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The card money endpoints through real security (issue #44).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:card-money;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class CardMoneyControllerTest {

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

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private String adminToken;
    private UUID cardId;

    @BeforeEach
    void seed() {
        User wanjiku = userRepository.save(new User("wanjiku@test.local", "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User admin = userRepository.save(new User("boss@test.local", "hash", "Ada", "Admin", UserRole.ROLE_ADMIN));
        Account current = accountRepository.save(new Account("1000004821", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode("4721")));
        Card card = new Card(wanjiku, current, "KES", new BigDecimal("300"));
        card.setStatus(CardStatus.ACTIVE);
        cardId = cardRepository.save(card).getId();
        customerToken = "Bearer " + jwtTokenProvider.generateToken(wanjiku);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin);
    }

    @Test
    void topUpThenWithdrawThenSeeTheHistory() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/top-up").header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(JSON).content("{\"fromAccountNumber\":\"1000004821\",\"amount\":500,\"pin\":\"4721\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CARD_TOPUP"))
                .andExpect(jsonPath("$.cardBalance").value(500.0));

        mockMvc.perform(post("/api/v1/cards/" + cardId + "/withdraw").header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(JSON).content("{\"toAccountNumber\":\"1000004821\",\"amount\":200,\"pin\":\"4721\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CARD_WITHDRAWAL"))
                .andExpect(jsonPath("$.cardBalance").value(300.0));

        mockMvc.perform(get("/api/v1/cards/" + cardId + "/transactions").header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/top-up").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"fromAccountNumber\":\"1000004821\",\"amount\":500,\"pin\":\"4721\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The Idempotency-Key header is required"));
    }

    @Test
    void wrongPinsGive401ThenTheLockGives423() throws Exception {
        String body = "{\"fromAccountNumber\":\"1000004821\",\"amount\":500,\"pin\":\"0000\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/cards/" + cardId + "/top-up").header("Authorization", customerToken)
                            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/top-up").header("Authorization", customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(JSON).content(body))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value("Too many wrong PINs. Try again in 15 minutes."));
    }

    @Test
    void anAdminCannotMoveCardMoney() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/top-up").header("Authorization", adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(JSON).content("{\"fromAccountNumber\":\"1000004821\",\"amount\":500,\"pin\":\"4721\"}"))
                .andExpect(status().isForbidden());
    }
}
