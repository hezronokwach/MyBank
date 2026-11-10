package com.example.mybank.services;

import com.example.mybank.dto.responses.CardMoveResponse;
import com.example.mybank.dto.responses.CardTransactionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Moving money between an account and a card, both ways
public interface CardMoneyService {
    CardMoveResponse topUp(String email, UUID cardId, String fromAccountNumber, BigDecimal amount,
                           String idempotencyKey, String pin);

    CardMoveResponse withdraw(String email, UUID cardId, String toAccountNumber, BigDecimal amount,
                              String idempotencyKey, String pin);

    List<CardTransactionResponse> getCardTransactions(String email, UUID cardId, int limit);
}
