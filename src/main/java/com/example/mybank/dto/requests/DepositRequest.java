package com.example.mybank.dto.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(
        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotNull(message = "Deposit amount is required")
        @DecimalMin(value = "0.01", message = "Deposit amount must be greater than zero")
        BigDecimal amount
) {
}