package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.IdempotencyKey;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.*;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.IdempotencyRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.TransferService;
import com.example.mybank.util.ReferenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransferServiceImpl implements TransferService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ReferenceGenerator referenceGenerator;
    private final TransactionMapper transactionMapper;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository, UserRepository userRepository, ReferenceGenerator referenceGenerator, TransactionMapper transactionMapper, IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.transactionMapper = transactionMapper;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TransactionResponse transfer(String email, String fromAccountNumber, String toAccountNumber, BigDecimal amount, String idempotencyKey) {
        Optional<IdempotencyKey> existingKey = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingKey.isPresent()) {
            try {
                return objectMapper.readValue(existingKey.get().getResponseBody(), TransactionResponse.class);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing cached response", e);
            }
        }
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Account fromAccount;
        Account toAccount;
        
        // Locking in order to prevent deadlock
        if (fromAccountNumber.compareTo(toAccountNumber) < 0) {
            fromAccount = accountRepository.findAndLockByAccountNumberAndUserId(fromAccountNumber, user.getId())
                    .orElseThrow(()-> new AccountNotFoundException("Account not found"));
            toAccount = accountRepository.findAndLockByAccountNumber(toAccountNumber)
                    .orElseThrow(()-> new AccountNotFoundException("Account not found"));
        } else {
            toAccount = accountRepository.findAndLockByAccountNumber(toAccountNumber)
                    .orElseThrow(()-> new AccountNotFoundException("Account not found"));
            fromAccount = accountRepository.findAndLockByAccountNumberAndUserId(fromAccountNumber, user.getId())
                    .orElseThrow(()-> new AccountNotFoundException("Account not found"));
        }

        if(fromAccount.equals(toAccount)) {
            throw new SelfTransferException("Cannot transfer to same account");
        }
        if (fromAccount.getStatus() != AccountStatus.ACTIVE){
            throw new AccountStatusException("Account not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE){
            throw new AccountStatusException("Account not active");
        }
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())){
            throw new CurrencyMatchException("Currencies do not match");
        }
        if (amount.signum() <= 0){
            throw new AmountException("Amount cannot be negative");
        }
        if (fromAccount.getBalance().compareTo(amount) <= 0){
            throw new InsufficientBalanceException("Insufficient balance");
        }
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        Transaction transaction = new Transaction(
                referenceGenerator.generateTransfer(),
                fromAccount,
                toAccount,
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS
        );
        transactionRepository.save(transaction);

        TransactionResponse response = transactionMapper.toResponse(transaction,fromAccount.getAccountNumber(),amount);

        try {
            IdempotencyKey record = new IdempotencyKey();
            record.setIdempotencyKey(idempotencyKey);
            record.setResponseBody(objectMapper.writeValueAsString(response));
            idempotencyRepository.save(record);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing response for idempotency", e);
        }

        return response;
    }
}
