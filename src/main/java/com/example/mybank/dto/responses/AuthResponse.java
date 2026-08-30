package com.example.mybank.dto.responses;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String email,
        AccountSummaryResponse account // 👈 Attached account context
) {
    public AuthResponse(String token, long expiresInMs, String email, AccountSummaryResponse account) {
        this(token, "Bearer", expiresInMs, email, account);
    }
}
