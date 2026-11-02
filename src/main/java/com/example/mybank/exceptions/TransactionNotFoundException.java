package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends BaseException {
    public TransactionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
