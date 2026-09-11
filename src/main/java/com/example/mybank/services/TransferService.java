package com.example.mybank.services;

import com.example.mybank.dto.responses.TransactionResponse;

import java.math.BigDecimal;

public interface TransferService {
    TransactionResponse transfer(String email, String fromAccount, String toAccount, BigDecimal amount, String idempotencyKey, String pin);

}
