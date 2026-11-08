package com.example.mybank.config;

import com.example.mybank.assistant.AssistantMetrics;
import com.example.mybank.assistant.dto.AssistantMetricsSummary;
import com.example.mybank.assistant.entity.MessageOutcome;
import com.example.mybank.assistant.entity.ToolOutcome;
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
import static org.hamcrest.Matchers.greaterThan;
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

    // ---- The summary the Admin Dashboard shows

    @Test
    void aCustomerCannotSeeTheAdminSummary() throws Exception {
        mockMvc.perform(get("/api/v1/admin/assistant/metrics").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminGetsTheSummaryInOneCall() throws Exception {
        // Other tests in this class record numbers too, so compare with what was there before
        AssistantMetricsSummary before = metrics.summary();

        metrics.recordAnswer(MessageOutcome.OK, 1000, 400, 40);
        metrics.recordAnswer(MessageOutcome.OK, 3000, 600, 60);
        metrics.recordAnswer(MessageOutcome.FALLBACK, 10000, null, null);
        metrics.recordToolCall("listMyAccounts", ToolOutcome.OK);
        metrics.recordToolCall("getAccount", ToolOutcome.NOT_FOUND);
        metrics.recordFeedback(1);

        mockMvc.perform(get("/api/v1/admin/assistant/metrics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answersOk").value(before.answersOk() + 2))
                .andExpect(jsonPath("$.answersFailed").value(before.answersFailed() + 1))
                .andExpect(jsonPath("$.averageLatencyMs", greaterThan(0)))
                .andExpect(jsonPath("$.inputTokens").value(before.inputTokens() + 1000))
                .andExpect(jsonPath("$.outputTokens").value(before.outputTokens() + 100))
                .andExpect(jsonPath("$.toolCalls").value(before.toolCalls() + 2))
                .andExpect(jsonPath("$.toolCallsNotOk").value(before.toolCallsNotOk() + 1))
                .andExpect(jsonPath("$.thumbsUp").value(before.thumbsUp() + 1))
                .andExpect(jsonPath("$.thumbsDown").value(before.thumbsDown()))
                .andExpect(jsonPath("$.countingSince").exists());
    }
}
