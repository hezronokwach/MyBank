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
import com.example.mybank.util.CardEncryptor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The card details endpoint through real security (issue #45).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:card-details;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173",
        "app.cards.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Transactional
class CardDetailsControllerTest {

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
    private CardEncryptor cardEncryptor;

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
        card.setCardNumberEncrypted(cardEncryptor.encrypt("9991234567890123"));
        card.setCvvEncrypted(cardEncryptor.encrypt("042"));
        card.setLast4("0123");
        card.setExpiryMonth(11);
        card.setExpiryYear(2029);
        cardId = cardRepository.save(card).getId();
        customerToken = "Bearer " + jwtTokenProvider.generateToken(wanjiku);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin);
    }

    @Test
    void theRightPinShowsTheDetailsAndTheBrowserMustNotKeepThem() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/details").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"pin\":\"4721\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.cardNumber").value("9991234567890123"))
                .andExpect(jsonPath("$.cvv").value("042"));
    }

    @Test
    void aWrongPinGets401AndNoDetails() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/details").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.cardNumber").doesNotExist());
    }

    @Test
    void theDetailsCannotBeFetchedWithAGetRequest() throws Exception {
        // Only POST exists, so a PIN can never be put in a URL
        mockMvc.perform(get("/api/v1/cards/" + cardId + "/details").header("Authorization", customerToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anAdminCannotSeeACustomersCardDetails() throws Exception {
        mockMvc.perform(post("/api/v1/cards/" + cardId + "/details").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"pin\":\"4721\"}"))
                .andExpect(status().isForbidden());
    }
}
