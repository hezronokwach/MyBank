package com.example.mybank.assistant.entity;

public enum PendingActionStatus {
    PENDING,    // Waiting for the customer to confirm or cancel
    CONFIRMED,  // The customer confirmed and it was done
    CANCELLED,  // The customer cancelled
    EXPIRED,    // Nobody confirmed it within 5 minutes
    FAILED      // The customer confirmed but it could not be done (for example the account was no longer active)
}
