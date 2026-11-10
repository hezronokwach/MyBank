package com.example.mybank.dto.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// POST /api/v1/auth/resend-verification
public record ResendVerificationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email
) {
}
