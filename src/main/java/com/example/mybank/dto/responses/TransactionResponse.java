package com.example.mybank.dto.responses;

import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String accountNumber,
        BigDecimal amountPaid,
        Instant createdAt
) {}
