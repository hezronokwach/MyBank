package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// A transfer card was confirmed without typing the PIN. Nothing is changed, so the customer can type it and try again.
public class PinRequiredException extends BaseException {
    public PinRequiredException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
