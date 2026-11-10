package com.example.mybank.dto.responses;

import com.example.mybank.enums.CardStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// A card application as the admin sees it. Never the card number or the CVV, only the last 4 digits.
public record AdminCardResponse(
        UUID id,
        CardStatus status,
        String customerName,
        String customerEmail,
        String linkedAccountNumber,
        BigDecimal feePaid,
        String last4,            // null until approved
        Integer expiryMonth,
        Integer expiryYear,
        Instant appliedAt,
        String decidedBy,        // the admin who approved or rejected it
        String rejectionReason
) {
}
