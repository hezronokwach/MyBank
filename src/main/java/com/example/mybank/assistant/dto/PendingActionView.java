package com.example.mybank.assistant.dto;

import com.example.mybank.assistant.entity.PendingActionStatus;
import com.example.mybank.assistant.entity.PendingActionType;

import java.time.Instant;
import java.util.UUID;

// What the app's confirm card shows. No full account number.
public record PendingActionView(
        UUID id,
        PendingActionType type,
        String summary,
        PendingActionStatus status,
        Instant expiresAt,
        String result
) {
}
