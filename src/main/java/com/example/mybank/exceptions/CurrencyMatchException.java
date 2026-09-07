package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class CurrencyMatchException extends BaseException{
    public CurrencyMatchException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
