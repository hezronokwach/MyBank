package com.example.mybank.dto.responses;

import com.example.mybank.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

// The result of moving money onto or off a card
public record CardMoveResponse(
        String reference,
        TransactionType type,      // CARD_TOPUP or CARD_WITHDRAWAL
        BigDecimal amount,
        String currency,
        String account,            // the other side, masked, for example "...4821"
        BigDecimal cardBalance,    // the card's balance after the move
        Instant createdAt
) {
}
