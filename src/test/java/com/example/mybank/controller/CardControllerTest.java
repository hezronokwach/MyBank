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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The card endpoints through real security (issue #42).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:card-endpoints;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class CardControllerTest {

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
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        User wanjiku = userRepository.save(new User("wanjiku@test.local", "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User otieno = userRepository.save(new User("otieno@test.local", "hash", "Otieno", "O", UserRole.ROLE_USER));
        User admin = userRepository.save(new User("boss@test.local", "hash", "Ada", "Admin", UserRole.ROLE_ADMIN));
        accountRepository.save(new Account("1000004821", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", wanjiku, passwordEncoder.encode("4721")));
        accountRepository.save(new Account("1000007777", new BigDecimal("1500"), AccountStatus.ACTIVE,
                AccountTier.TIER_2_VERIFIED, AccountType.CURRENT, "KES", otieno, passwordEncoder.encode("1111")));
        customerToken = "Bearer " + jwtTokenProvider.generateToken(wanjiku);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin);
    }

    @Test
    void aCustomerAppliesAndSeesTheCardWaiting() throws Exception {
        mockMvc.perform(post("/api/v1/cards").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"accountNumber\":\"1000004821\",\"pin\":\"4721\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.linkedAccount").value("...4821"))
                .andExpect(jsonPath("$.feePaid").value(300));

        mockMvc.perform(get("/api/v1/cards/me").header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"))
                // Never the encrypted or plain card number or CVV
                .andExpect(content().string(not(containsString("cardNumber"))))
                .andExpect(content().string(not(containsString("cvv"))));
    }

    @Test
    void theFeeComesFromTheSettings() throws Exception {
        mockMvc.perform(get("/api/v1/cards/fee").header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").value(300.0))
                .andExpect(jsonPath("$.currency").value("KES"));
    }

    @Test
    void aSecondApplicationGets409() throws Exception {
        String body = "{\"accountNumber\":\"1000004821\",\"pin\":\"4721\"}";
        mockMvc.perform(post("/api/v1/cards").header("Authorization", customerToken).contentType(JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cards").header("Authorization", customerToken).contentType(JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a card"));
    }

    @Test
    void someoneElsesAccountGets404() throws Exception {
        mockMvc.perform(post("/api/v1/cards").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"accountNumber\":\"1000007777\",\"pin\":\"1111\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aWrongPinGets401() throws Exception {
        mockMvc.perform(post("/api/v1/cards").header("Authorization", customerToken)
                        .contentType(JSON).content("{\"accountNumber\":\"1000004821\",\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCannotApplyAndNoLoginGets403() throws Exception {
        mockMvc.perform(post("/api/v1/cards").header("Authorization", adminToken)
                        .contentType(JSON).content("{\"accountNumber\":\"1000004821\",\"pin\":\"4721\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/cards/me"))
                .andExpect(status().isForbidden());
    }
}
