package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BaseException {

    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
