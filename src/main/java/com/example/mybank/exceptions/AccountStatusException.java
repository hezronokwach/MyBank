package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class AccountStatusException extends BaseException {
    public AccountStatusException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
