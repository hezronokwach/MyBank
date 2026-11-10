package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class CardNotFoundException extends BaseException {
    public CardNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
