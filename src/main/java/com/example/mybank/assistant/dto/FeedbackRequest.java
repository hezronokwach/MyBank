package com.example.mybank.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FeedbackRequest(
        @NotNull(message = "Message id is required")
        UUID messageId,

        // 1 = thumbs up, -1 = thumbs down (0 is rejected in FeedbackService)
        @NotNull(message = "Rating is required")
        @Min(value = -1, message = "Rating must be 1 or -1")
        @Max(value = 1, message = "Rating must be 1 or -1")
        Integer rating,

        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment
) {
}
