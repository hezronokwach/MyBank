package com.example.mybank.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// POST /api/v1/cards/{id}/withdraw: move money from the card back to an account
public record CardWithdrawRequest(
        @NotBlank(message = "Account number is required")
        String toAccountNumber,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @NotBlank(message = "PIN is required")
        String pin
) {
    // Never print the PIN, for example if this object ends up in a log line
    @Override
    public String toString() {
        return "CardWithdrawRequest[toAccountNumber=" + toAccountNumber + ", amount=" + amount + ", pin=****]";
    }
}
