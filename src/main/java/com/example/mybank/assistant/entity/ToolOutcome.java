package com.example.mybank.assistant.entity;

public enum ToolOutcome {
    OK,
    AMBIGUOUS, // More than one of the customer's accounts matched; the model asks which one
    NOT_FOUND,
    DENIED,    // Blocked by a guardrail, such as the tool-call limit
    ERROR
}
