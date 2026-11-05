package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidFeedbackException extends BaseException {
    public InvalidFeedbackException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
