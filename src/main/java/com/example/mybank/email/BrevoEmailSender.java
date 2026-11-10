package com.example.mybank.email;

import com.example.mybank.exceptions.EmailNotSentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Brevo's HTTPS API (free plan: 300 emails a day).
 *
 * The API is used instead of SMTP because Render's free plan blocks the SMTP ports (25, 465, 587).
 * This is a normal HTTPS request, so it works there.
 *
 * Docs: POST https://api.brevo.com/v3/smtp/email with the "api-key" header.
 */
public class BrevoEmailSender implements EmailSender {

    static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(RestClient restClient, String apiKey, String fromEmail, String fromName) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(String toEmail, String toName, String subject, String text) {
        // The JSON Brevo expects:
        // { "sender": {...}, "to": [ {...} ], "subject": "...", "textContent": "..." }
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", fromEmail);
        sender.put("name", fromName);

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", toEmail);
        recipient.put("name", toName);
        List<Map<String, Object>> to = new ArrayList<>();
        to.add(recipient);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", to);
        body.put("subject", subject);
        body.put("textContent", text);

        try {
            restClient.post()
                    .uri(BREVO_URL)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Never log the email text: it holds the confirmation link
            log.error("Brevo did not send the email: {}", e.getMessage());
            throw new EmailNotSentException("We couldn't send the confirmation email. Please try again in a few minutes.");
        }
    }
}
