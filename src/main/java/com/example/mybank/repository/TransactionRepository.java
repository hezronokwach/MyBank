package com.example.mybank.repository;

import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByToAccountAndType(Account toAccount, TransactionType type);
}
