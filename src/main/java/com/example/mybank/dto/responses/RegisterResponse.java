package com.example.mybank.dto.responses;

// POST /api/v1/auth/register: no login token yet. The user must confirm their email first.
public record RegisterResponse(
        String email,
        String message
) {
}
