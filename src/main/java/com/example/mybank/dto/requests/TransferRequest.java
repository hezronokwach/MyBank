package com.example.mybank.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "Source account number is required")
        String fromAccountNumber,

        @NotBlank(message = "Destination account number is required")
        String toAccountNumber,

        @NotNull(message = "Transfer amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
        BigDecimal amount,
        @NotBlank(message = "PIN is required")
        String pin
) {
}