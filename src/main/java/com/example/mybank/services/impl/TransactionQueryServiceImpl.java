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

        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        return transactionRepository.findRecentForAccount(account.getId(), PageRequest.of(0, pageSize))
                .stream()
                .map(transaction -> toHistoryResponse(transaction, account.getId()))
                .toList();
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
        return account != null && account.getUser().getId().equals(user.getId());
    }

    private TransactionHistoryResponse toHistoryResponse(Transaction transaction, UUID viewingAccountId) {
        boolean outgoing = transaction.getFromAccount() != null
                && transaction.getFromAccount().getId().equals(viewingAccountId);
        Account counterparty = outgoing ? transaction.getToAccount() : transaction.getFromAccount();

        return new TransactionHistoryResponse(
                transaction.getCreatedAt(),
                transaction.getType(),
                transaction.getAmount(),
                outgoing ? TransactionDirection.OUT : TransactionDirection.IN,
                counterparty == null ? null : last4(counterparty.getAccountNumber()),
                transaction.getReference(),
                transaction.getStatus()
        );
    }

    private String last4(String accountNumber) {
        return accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}
