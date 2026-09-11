package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class TransactionNotAllowedException extends BaseException {
    public TransactionNotAllowedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
