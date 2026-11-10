package com.example.mybank.assistant.dto;

// The body of POST /api/v1/assistant/actions/{id}/confirm.
// pin is only needed for a transfer; a freeze sends no body at all.
public record ConfirmActionRequest(
        String pin
) {
    // Never print the PIN, for example if this object ends up in a log line
    @Override
    public String toString() {
        return "ConfirmActionRequest[pin=****]";
    }
}
