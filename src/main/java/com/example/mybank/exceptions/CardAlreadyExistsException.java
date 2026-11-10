package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// A customer can have only one card that is waiting for approval, active or frozen
public class CardAlreadyExistsException extends BaseException {
    public CardAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
