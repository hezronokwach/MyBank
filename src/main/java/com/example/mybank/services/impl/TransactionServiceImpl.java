package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.*;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.TransactionService;
import com.example.mybank.util.ReferenceGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionServiceImpl implements TransactionService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ReferenceGenerator referenceGenerator;
    private final TransactionRepository transactionRespository;
    private final TransactionMapper transactionMapper;

    public TransactionServiceImpl(AccountRepository accountRepository, UserRepository userRepository, ReferenceGenerator referenceGenerator, TransactionRepository transactionRespository, TransactionMapper transactionMapper) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.transactionRespository = transactionRespository;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(String userEmail, String accountNumber, BigDecimal amount) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if(account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active") {
            };
        }
        if (amount.signum() <= 0) {
            throw new AmountException("Amount cannot be negative");
        }
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);
        Transaction transaction = new Transaction(
                referenceGenerator.generateDeposit(),
                null,
                account,
                amount,
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS
        );
        transactionRespository.save(transaction);
        return transactionMapper.toResponse(transaction, account.getAccountNumber(),newBalance);

    }

    @Override
    @Transactional
    public TransactionResponse withdraw(String userEmail, String accountNumber, BigDecimal amount) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if(account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active") {
            };
        }
        if (amount.signum() <= 0) {
            throw new AmountException("Amount cannot be negative");
        }
        if(account.getBalance().compareTo(amount) <= 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);
        Transaction transaction = new Transaction(
                referenceGenerator.generateWithdrawal(),
                account,
                null,
                amount,
                TransactionType.WITHDRAWAL,
                TransactionStatus.SUCCESS
        );
        transactionRespository.save(transaction);
        return transactionMapper.toResponse(transaction, account.getAccountNumber(),newBalance);
    }
}
