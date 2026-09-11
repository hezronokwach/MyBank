package com.example.mybank.dto.requests;

import com.example.mybank.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotNull(message = "Account type is required")
        AccountType type,
        @NotNull(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency,
        @NotNull(message = "PIN is required")
        @Size(min = 4, max = 4, message = "PIN must be 4 characters")
        String pin
) {}
