package com.example.mybank.email;

import com.example.mybank.exceptions.EmailNotSentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used in the prod profile when BREVO_API_KEY is missing. It refuses to send, so sign-up fails with a clear
 * message instead of quietly printing confirmation links in the server's logs.
 */
public class NotConfiguredEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NotConfiguredEmailSender.class);

    @Override
    public void send(String toEmail, String toName, String subject, String text) {
        log.error("Email is not set up: add BREVO_API_KEY and MAIL_FROM to send confirmation emails");
        throw new EmailNotSentException("Sign-up is not available right now because email is not set up. Please try again later.");
    }
}
