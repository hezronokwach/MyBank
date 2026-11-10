package com.example.mybank.enums;

public enum CardStatus {
    // Applied and fee paid, waiting for an admin
    PENDING_APPROVAL,
    // Issued: it has a card number and can hold money
    ACTIVE,
    // The admin said no; the fee is refunded
    REJECTED,
    // Can't be used for now
    FROZEN
}
