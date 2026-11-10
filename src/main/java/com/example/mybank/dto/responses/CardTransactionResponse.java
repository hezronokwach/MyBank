package com.example.mybank.dto.responses;

import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

// One line of a card's history, seen from the card: IN = money added to the card, OUT = money taken off it
public record CardTransactionResponse(
        Instant date,
        TransactionType type,
        TransactionDirection direction,
        BigDecimal amount,
        String account,            // the account on the other side, masked
        String reference
) {
}
