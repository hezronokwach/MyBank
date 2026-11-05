package com.example.mybank.assistant.entity;

public enum MessageOutcome {
    OK,
    FALLBACK,              // The model timed out, errored or gave an empty answer; the customer got the fallback message
    RATE_LIMITED,          // The customer sent more than 10 messages in a minute
    RATE_LIMITED_PROVIDER  // Groq's own usage limit was reached
}
