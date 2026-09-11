package com.example.mybank.dto.requests;

import jakarta.validation.constraints.NotNull;

public record PinRequest(
        @NotNull(message = "PIN is required")
        String pin
) {}
