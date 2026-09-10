package com.example.mybank.services;

import com.example.mybank.dto.responses.TransactionResponse;

import java.math.BigDecimal;

public interface TransactionService {
    TransactionResponse deposit(String userEmail, String accountNumber, BigDecimal amount);
    TransactionResponse withdraw(String userEmail, String accountNumber,BigDecimal amount);
}
