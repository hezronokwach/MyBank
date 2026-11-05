package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends BaseException {
    public MessageNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
