package com.example.mybank.assistant.dto;

import java.util.UUID;

public record FeedbackResponse(
        UUID feedbackId,
        UUID messageId,
        int rating,
        String comment
) {
}
