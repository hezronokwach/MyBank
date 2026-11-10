package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// A confirmation link that doesn't exist, was already used, or has expired
public class InvalidVerificationLinkException extends BaseException {
    public InvalidVerificationLinkException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
