package com.example.mybank.services;

import com.example.mybank.entity.User;

public interface EmailVerificationService {
    // Creates a new link for the user and emails it
    void sendVerification(User user);

    // Marks the user's email as confirmed, if the token is valid
    void verify(String token);

    // Sends a new link, if this email belongs to a user who hasn't confirmed yet (at most 3 an hour)
    void resend(String email);
}
