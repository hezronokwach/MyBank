package com.example.mybank.dto.responses;

import com.example.mybank.enums.CardStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// A card as the customer sees it in lists: never the full card number or the CVV
public record CardResponse(
        UUID id,
        CardStatus status,
        String last4,            // null until an admin approves the card
        BigDecimal balance,
        String currency,
        Integer expiryMonth,     // null until approved
        Integer expiryYear,      // null until approved
        String linkedAccount,    // masked, for example "...4821"
        BigDecimal feePaid,
        String rejectionReason,  // only for a rejected card
        Instant createdAt
) {
}
