package com.example.mybank.exceptions;


import org.springframework.http.HttpStatus;

public class SelfTransferException extends BaseException{
    public SelfTransferException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
