package com.example.mybank.mappers;

import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.entity.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction, String accountNumber, BigDecimal runningBalance) {
        return new TransactionResponse(
                transaction.getReference(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                accountNumber,
                runningBalance,
                transaction.getCreatedAt()
        );
    }
}
