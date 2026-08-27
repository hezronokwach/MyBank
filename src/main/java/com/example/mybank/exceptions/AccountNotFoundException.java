package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BaseException {

    public AccountNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
