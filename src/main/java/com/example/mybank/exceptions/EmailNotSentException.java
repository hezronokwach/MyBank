package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// The confirmation email couldn't be sent (email isn't set up, or Brevo said no). Nothing is saved, so the user can try again.
public class EmailNotSentException extends BaseException {
    public EmailNotSentException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
