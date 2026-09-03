package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class AmountException extends BaseException {
    public AmountException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
