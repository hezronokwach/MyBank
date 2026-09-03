package com.example.mybank.services.impl;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    public AccountServiceImpl(UserRepository userRepository,
                              AccountRepository accountRepository,
                              AccountMapper accountMapper) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        List<Account> accounts = accountRepository.findByUserId(user.getId());
        return accountMapper.toAccountResponseList(accounts);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String email, String accountNumber) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));

        return accountMapper.toAccountResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse updateAccountStatus(String accountNumber, AccountStatusUpdateRequest request) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

        if (request.status() != null) {
            account.setStatus(request.status());
        }
        if (request.tier() != null) {
            account.setTier(request.tier());
        }

        Account updatedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(updatedAccount);
    }
}