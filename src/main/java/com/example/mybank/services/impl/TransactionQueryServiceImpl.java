package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.TransactionHistoryResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.TransactionNotFoundException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.TransactionQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionQueryServiceImpl implements TransactionQueryService {

    static final int MAX_LIMIT = 20;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionQueryServiceImpl(UserRepository userRepository,
                                       AccountRepository accountRepository,
                                       TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getRecent(String email, String accountNumber, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));

        // At least 1, at most 20
        int pageSize = limit;
        if (pageSize < 1) {
            pageSize = 1;
        }
        if (pageSize > MAX_LIMIT) {
            pageSize = MAX_LIMIT;
        }

        List<Transaction> transactions =
                transactionRepository.findRecentForAccount(account.getId(), PageRequest.of(0, pageSize));

        List<TransactionHistoryResponse> history = new ArrayList<>();
        for (Transaction transaction : transactions) {
            history.add(toHistoryResponse(transaction, account.getId()));
        }
        return history;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionHistoryResponse findByReference(String email, String reference) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        // Same error whether the reference doesn't exist or belongs to someone else, so nothing leaks
        Transaction transaction = transactionRepository.findByReferenceWithAccounts(reference)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction " + reference + " not found"));

        Account ownSide;
        if (isOwnedBy(transaction.getToAccount(), user) && !isOwnedBy(transaction.getFromAccount(), user)) {
            ownSide = transaction.getToAccount();
        } else if (isOwnedBy(transaction.getFromAccount(), user)) {
            // Also covers transfers between two of the user's own accounts, shown from the sending side
            ownSide = transaction.getFromAccount();
        } else {
            throw new TransactionNotFoundException("Transaction " + reference + " not found");
        }

        return toHistoryResponse(transaction, ownSide.getId());
    }

    private boolean isOwnedBy(Account account, User user) {
        if (account == null) {
            return false; // Deposits have no "from" account and withdrawals have no "to" account
        }
        return account.getUser().getId().equals(user.getId());
    }

    private TransactionHistoryResponse toHistoryResponse(Transaction transaction, UUID viewingAccountId) {
        // Money went OUT if the account we're looking from is the sending side
        boolean outgoing = false;
        if (transaction.getFromAccount() != null && transaction.getFromAccount().getId().equals(viewingAccountId)) {
            outgoing = true;
        }

        TransactionDirection direction;
        Account ownSide;       // The customer's account in this transaction
        Account counterparty;  // The other account, or null for deposits and withdrawals
        if (outgoing) {
            direction = TransactionDirection.OUT;
            ownSide = transaction.getFromAccount();
            counterparty = transaction.getToAccount();
        } else {
            direction = TransactionDirection.IN;
            ownSide = transaction.getToAccount();
            counterparty = transaction.getFromAccount();
        }

        String counterpartyLast4 = null;
        if (counterparty != null) {
            counterpartyLast4 = last4(counterparty.getAccountNumber());
        }

        return new TransactionHistoryResponse(
                transaction.getCreatedAt(),
                transaction.getType(),
                transaction.getAmount(),
                ownSide.getCurrency(), // Transfers only run between accounts of the same currency
                direction,
                counterpartyLast4,
                transaction.getReference(),
                transaction.getStatus()
        );
    }

    private String last4(String accountNumber) {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return accountNumber.substring(accountNumber.length() - 4);
    }
}
