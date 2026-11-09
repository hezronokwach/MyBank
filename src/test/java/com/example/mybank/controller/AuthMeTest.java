package com.example.mybank.controller;

import com.example.mybank.entity.User;
import com.example.mybank.enums.UserRole;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/auth/me gives the signed-in user their own name, for the Dashboard greeting.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-me;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=http://localhost:5173"
})
@AutoConfigureMockMvc
@Transactional
class AuthMeTest {

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsTheSignedInUsersOwnName() throws Exception {
        User wanjiku = userRepository.save(new User("wanjiku@test.local", "secret-hash", "Wanjiku", "Kamau", UserRole.ROLE_USER));
        String token = jwtTokenProvider.generateToken(wanjiku);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Wanjiku"))
                .andExpect(jsonPath("$.lastName").value("Kamau"))
                .andExpect(jsonPath("$.email").value("wanjiku@test.local"))
                // never the password
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void needsALogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me/role"))
                .andExpect(status().isForbidden());
    }
}
