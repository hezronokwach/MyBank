package com.example.mybank.dto.requests;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/auth/verify-email: the token from the link in the email
public record VerifyEmailRequest(
        @NotBlank(message = "The link is missing its token")
        String token
) {
}
