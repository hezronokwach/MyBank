package com.example.mybank.config;

import com.example.mybank.assistant.AssistantMetrics;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can see /actuator (issue #19):
 *   /actuator/health   anyone, so Render can check that the app is up
 *   /actuator/metrics  admins only
 *
 * Runs the whole app with real security and real JWTs on an in-memory database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:actuator-security;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class ActuatorSecurityTest {

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssistantMetrics metrics;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void seed() {
        User customer = userRepository.save(new User("customer@test.local", "hash", "Wanjiku", "K", UserRole.ROLE_USER));
        User admin = userRepository.save(new User("metrics-admin@test.local", "hash", "Ada", "Admin", UserRole.ROLE_ADMIN));
        customerToken = jwtTokenProvider.generateToken(customer);
        adminToken = jwtTokenProvider.generateToken(admin);
    }

    @Test
    void healthIsPublicAndOnlySaysUpOrDown() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // No details such as the database name
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void metricsNeedALogin() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotSeeMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics/assistant.requests").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesTheAssistantMetrics() throws Exception {
        // One answer, as the assistant would record it
        metrics.recordAnswer(MessageOutcome.OK, 1200, 300, 50);

        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItems("assistant.requests", "assistant.tokens",
                        "assistant.latency", "assistant.cost.estimate")));

        mockMvc.perform(get("/actuator/metrics/assistant.requests").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[0].tag").value("outcome"))
                .andExpect(jsonPath("$.availableTags[0].values", hasItem("OK")));

        mockMvc.perform(get("/actuator/metrics/assistant.tokens")
                        .param("tag", "type:input")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // at least 300: other tests in this class add tokens too
                .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(300.0)));
    }

}
