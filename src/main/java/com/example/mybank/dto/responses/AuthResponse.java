package com.example.mybank.dto.responses;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expireInMs,
        UserResponse user
) {
}
