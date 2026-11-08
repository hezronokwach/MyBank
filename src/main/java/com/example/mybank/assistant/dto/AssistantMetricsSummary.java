package com.example.mybank.assistant.dto;

import java.time.Instant;

/**
 * The assistant's numbers for the Admin Dashboard, counted since the app last started.
 */
public record AssistantMetricsSummary(
        Instant countingSince,

        // Answers
        long answersOk,
        long answersFailed,          // the model failed or timed out (FALLBACK)
        long answersProviderLimited, // Groq's own usage limit was reached
        long rateLimited,            // a customer sent too many messages in a minute
        long averageLatencyMs,

        // Tokens and cost
        long inputTokens,
        long outputTokens,
        double estimatedCostUsd,

        // Tools
        long toolCalls,
        long toolCallsNotOk,         // DENIED, NOT_FOUND, AMBIGUOUS or ERROR

        // Things that were stopped
        long blockedRateLimit,
        long blockedToolLimit,
        long blockedOutputGuard,

        // Confirm cards
        long actionsCreated,
        long actionsConfirmed,
        long actionsCancelled,
        long actionsExpired,
        long actionsFailed,

        // Feedback
        long thumbsUp,
        long thumbsDown
) {
}
