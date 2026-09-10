package com.example.mybank.dto.responses;

import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountType type,
        AccountStatus status,
        AccountTier tier
) {
}
