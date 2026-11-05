package com.example.mybank.assistant.dto;

import com.example.mybank.assistant.entity.MessageOutcome;

import java.util.List;
import java.util.UUID;

// Named ChatAnswer, not ChatResponse, because Spring AI already has a class called ChatResponse
public record ChatAnswer(
        UUID conversationId,
        UUID messageId,
        String text,
        MessageOutcome outcome,
        List<String> sources,        // What the answer was based on, such as "your accounts"
        List<PendingActionView> pendingActions  // Actions waiting for the customer to confirm, one confirm card each
) {
}
