package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// The action was already confirmed, cancelled or expired, so it can't be confirmed or cancelled now
public class PendingActionConflictException extends BaseException {
    public PendingActionConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
