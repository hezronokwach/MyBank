package com.example.mybank.dto.requests;

import jakarta.validation.constraints.NotBlank;

// POST /api/v1/cards/{id}/details. A POST, not a GET, so the PIN is never in a URL or the browser's history.
public record CardDetailsRequest(
        @NotBlank(message = "PIN is required")
        String pin
) {
    // Never print the PIN, for example if this object ends up in a log line
    @Override
    public String toString() {
        return "CardDetailsRequest[pin=****]";
    }
}
