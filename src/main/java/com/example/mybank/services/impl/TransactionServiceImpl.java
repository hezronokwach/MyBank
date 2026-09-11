package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.*;
import com.example.mybank.mappers.TransactionMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.TransactionService;
import com.example.mybank.util.ReferenceGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    public TransactionServiceImpl(AccountRepository accountRepository, UserRepository userRepository, ReferenceGenerator referenceGenerator, TransactionRepository transactionRespository, TransactionMapper transactionMapper, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.transactionRespository = transactionRespository;
        this.transactionMapper = transactionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(String userEmail, String accountNumber, BigDecimal amount, String pin) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }
        if(account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active");
        }
        if (account.getType().name().startsWith("FIXED")) {
            if (transactionRespository.existsByToAccountAndType(account, TransactionType.DEPOSIT)) {
                throw new TransactionNotAllowedException("Fixed accounts only allow one deposit");
            }
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
    public TransactionResponse withdraw(String userEmail, String accountNumber, BigDecimal amount, String pin) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }
        if(account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account is not active");
        }
        if (account.getType() == AccountType.FIXED_01 || account.getType() == AccountType.FIXED_02 || account.getType() == AccountType.FIXED_03) {
            throw new TransactionNotAllowedException("Transactions not allowed for fixed accounts");
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
