package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BaseException {
    public UserNotFoundException(String message){
        super(message, HttpStatus.NOT_FOUND);
    }

}
