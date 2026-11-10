package com.example.mybank.controller;

import com.example.mybank.email.EmailSender;
import com.example.mybank.exceptions.EmailNotSentException;
import com.example.mybank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-up with email confirmation, through the real endpoints (issue #40).
 * The email sender is a mock, so the test can read the link instead of sending it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "app.jwt.expiration=3600000",
        "app.react-url=https://mybank.test"
})
@AutoConfigureMockMvc
@Transactional
class EmailVerificationFlowTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String REGISTER = "{\"email\":\"amina@test.local\",\"password\":\"secret123\","
            + "\"firstName\":\"Amina\",\"lastName\":\"Ali\",\"pin\":\"4721\"}";
    private static final String LOGIN = "{\"email\":\"amina@test.local\",\"password\":\"secret123\"}";

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void signUpThenConfirmThenSignIn() throws Exception {
        // 1. Sign up: no login token, just a message
        mockMvc.perform(post("/api/v1/auth/register").contentType(JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("amina@test.local"))
                .andExpect(jsonPath("$.message").value("We sent a link to amina@test.local. Open it to confirm your email, then sign in."))
                .andExpect(jsonPath("$.token").doesNotExist());

        // 2. Signing in before confirming is refused
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON).content(LOGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Confirm your email first. Check your inbox for the link."));

        // 3. Open the link from the email
        String token = tokenFromEmail();
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your email is confirmed. You can sign in now."));

        // 4. Now signing in works
        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // 5. The same link doesn't work twice
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This link has expired or was already used"));
    }

    @Test
    void aWrongPasswordStillSaysWrongPasswordNotConfirmYourEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(JSON).content(REGISTER))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"amina@test.local\",\"password\":\"wrong-one\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendGivesTheSameAnswerForAnyEmail() throws Exception {
        String sameAnswer = "If that email is waiting to be confirmed, we sent a new link. Check your inbox.";

        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(JSON).content("{\"email\":\"nobody@test.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(sameAnswer));
        verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/register").contentType(JSON).content(REGISTER));
        mockMvc.perform(post("/api/v1/auth/resend-verification").contentType(JSON).content("{\"email\":\"amina@test.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(sameAnswer));
    }

    // Runs outside the test's own transaction, the same way a real request runs,
    // so we can see that the sign-up was really undone
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ifTheEmailCannotBeSentNothingIsSavedAndTheUserCanTryAgain() throws Exception {
        doThrow(new EmailNotSentException("We couldn't send the confirmation email. Please try again in a few minutes."))
                .when(emailSender).send(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/register").contentType(JSON).content(REGISTER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("We couldn't send the confirmation email. Please try again in a few minutes."));

        assertThat(userRepository.findByEmail("amina@test.local")).isEmpty();
    }

    // The token at the end of the link in the email the mock sender received
    private String tokenFromEmail() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("amina@test.local"), anyString(), anyString(), text.capture());
        String emailText = text.getValue();
        assertThat(emailText).contains("https://mybank.test/verify-email?token=");
        int start = emailText.indexOf("token=") + "token=".length();
        int end = emailText.indexOf('\n', start);
        return emailText.substring(start, end);
    }
}
