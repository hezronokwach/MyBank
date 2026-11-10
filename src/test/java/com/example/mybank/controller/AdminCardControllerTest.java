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
import com.example.mybank.services.CardService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin card endpoints through real security (issue #43).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-cards;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173",
        "app.cards.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Transactional
class AdminCardControllerTest {

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
    private CardService cardService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;
    private UUID cardId;

    @BeforeEach
    void seed() {
        User wanjiku = userRepository.save(new User("wanjiku@test.local", "hash", "Wanjiku", "Kamau", UserRole.ROLE_USER));
        User admin = userRepository.save(new User("boss@test.local", "hash", "Ada", "Admin", UserRole.ROLE_ADMIN));
        accountRepository.save(new Account("1000004821", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode("4721")));
        cardId = cardService.applyForCard("wanjiku@test.local", "1000004821", "4721").id();
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin);
        customerToken = "Bearer " + jwtTokenProvider.generateToken(wanjiku);
    }

    @Test
    void anAdminSeesTheApplicationAndApprovesIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cards").param("status", "PENDING_APPROVAL").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Wanjiku Kamau"))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));

        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.decidedBy").value("boss@test.local"))
                // Only the last 4 digits; never the full number, the CVV or their encrypted form
                .andExpect(content().string(not(containsString("cardNumber"))))
                .andExpect(content().string(not(containsString("cvv"))));

        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectingNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/reject").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/reject").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"reason\":\"Please verify your ID first\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Please verify your ID first"));

        // The customer sees why
        mockMvc.perform(get("/api/v1/cards/me").header("Authorization", customerToken))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].rejectionReason").value("Please verify your ID first"));
    }

    @Test
    void aCustomerCannotUseTheAdminCardEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cards").header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/approve").header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/cards/" + cardId + "/reject").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"reason\":\"self\"}"))
                .andExpect(status().isForbidden());
    }
}
