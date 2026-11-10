package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// The right password, but the user hasn't clicked the link in the confirmation email yet
public class EmailNotVerifiedException extends BaseException {
    public EmailNotVerifiedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
