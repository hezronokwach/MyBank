package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class PendingActionNotFoundException extends BaseException {
    public PendingActionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
