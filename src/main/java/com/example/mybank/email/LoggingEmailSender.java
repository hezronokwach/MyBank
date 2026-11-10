package com.example.mybank.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For running MyBank on your own computer without a Brevo key: instead of sending the email,
 * it prints it (link included) in the backend's log, so you can copy the link from there.
 *
 * EmailConfig never uses this in the prod profile, because the link must not end up in a server's logs.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String toEmail, String toName, String subject, String text) {
        log.info("""

                ===== EMAIL (not sent: no BREVO_API_KEY, so it is printed here) =====
                To: {} <{}>
                Subject: {}

                {}
                =====================================================================""", toName, toEmail, subject, text);
    }
}
