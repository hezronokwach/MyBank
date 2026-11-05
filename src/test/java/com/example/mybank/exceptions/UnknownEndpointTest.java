package com.example.mybank.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unknown URLs and wrong HTTP methods used to fall into the catch-all handler and return 500.
 * Starts the whole app with test settings, so it runs without local config.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:unknown-endpoint;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
class UnknownEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No endpoint found for GET /api/v1/no-such-endpoint"))
                .andExpect(jsonPath("$.path").value("/api/v1/no-such-endpoint"));
    }

    @Test
    @WithMockUser
    void invalidIdInTheUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/actions/not-a-uuid/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for actionId: not-a-uuid"));
    }

    @Test
    @WithMockUser
    void wrongHttpMethodReturns405() throws Exception {
        // /api/v1/auth/register only accepts POST
        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("GET is not supported for /api/v1/auth/register"));
    }
}
