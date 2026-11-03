package com.example.mybank.dto.responses;

import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryResponse(
        Instant date,
        TransactionType type,
        BigDecimal amount,
        String currency,
        TransactionDirection direction,
        String counterpartyLast4, // null for deposits and withdrawals, which have no other account
        String reference,
        TransactionStatus status
) {
}
