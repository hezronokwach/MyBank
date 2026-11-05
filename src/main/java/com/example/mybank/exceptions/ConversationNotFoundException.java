package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class ConversationNotFoundException extends BaseException {
    public ConversationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
