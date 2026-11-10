package com.example.mybank.dto.requests;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/cards: which account pays the fee, and that account's PIN
public record CardApplicationRequest(
        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotBlank(message = "PIN is required")
        String pin
) {
    // Never print the PIN, for example if this object ends up in a log line
    @Override
    public String toString() {
        return "CardApplicationRequest[accountNumber=" + accountNumber + ", pin=****]";
    }
}
