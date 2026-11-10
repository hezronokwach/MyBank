package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// The card was already approved or rejected, maybe by another admin a moment ago
public class CardStatusConflictException extends BaseException {
    public CardStatusConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
