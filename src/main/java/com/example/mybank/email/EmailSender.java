package com.example.mybank.email;

// Sends one email. EmailConfig picks which version the app uses.
public interface EmailSender {
    void send(String toEmail, String toName, String subject, String text);
}
