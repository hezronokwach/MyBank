package com.example.mybank.enums;

public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    // The fee taken from an account when the customer applies for a virtual card
    CARD_FEE,
    // The card fee given back when an admin rejects the card
    CARD_FEE_REFUND,
    // Money moved from an account onto the customer's card
    CARD_TOPUP,
    // Money moved from the card back to an account
    CARD_WITHDRAWAL
}
