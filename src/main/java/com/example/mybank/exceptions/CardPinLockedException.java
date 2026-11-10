package com.example.mybank.exceptions;

import org.springframework.http.HttpStatus;

// 3 wrong PINs in a row: card actions are locked for 15 minutes
public class CardPinLockedException extends BaseException {
    public CardPinLockedException(String message) {
        super(message, HttpStatus.LOCKED);
    }
}
