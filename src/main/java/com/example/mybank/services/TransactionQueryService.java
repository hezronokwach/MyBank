package com.example.mybank.services;

import com.example.mybank.dto.responses.TransactionHistoryResponse;

import java.util.List;

public interface TransactionQueryService {
    List<TransactionHistoryResponse> getRecent(String email, String accountNumber, int limit);
    TransactionHistoryResponse findByReference(String email, String reference);
}
