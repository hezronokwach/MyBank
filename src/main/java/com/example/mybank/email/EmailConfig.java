package com.example.mybank.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

/**
 * Picks how the app sends email, when it starts:
 *   - BREVO_API_KEY is set:          real emails through Brevo (MAIL_FROM must be set too)
 *   - no key, prod profile (Render): refuse to send, so sign-up says email isn't set up
 *   - no key, anywhere else:         print the email in the log (for local development)
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    public EmailSender emailSender(@Value("${app.mail.brevo-api-key:}") String brevoApiKey,
                                   @Value("${app.mail.from:}") String fromEmail,
                                   @Value("${app.mail.from-name:MyBank}") String fromName,
                                   RestClient.Builder restClientBuilder,
                                   Environment environment) {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            if (fromEmail == null || fromEmail.isBlank()) {
                throw new IllegalStateException("BREVO_API_KEY is set, so MAIL_FROM must be set too "
                        + "(the sender address you verified in Brevo)");
            }
            log.info("Emails are sent through Brevo from {}", fromEmail);
            return new BrevoEmailSender(restClientBuilder.build(), brevoApiKey, fromEmail, fromName);
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            log.warn("BREVO_API_KEY is not set: sign-up will fail until email is set up");
            return new NotConfiguredEmailSender();
        }

        log.info("BREVO_API_KEY is not set: emails are printed in this log instead of sent");
        return new LoggingEmailSender();
    }
}
