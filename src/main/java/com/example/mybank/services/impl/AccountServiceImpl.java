package com.example.mybank.services.impl;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.requests.AccountUpdateInfoRequest;
import com.example.mybank.dto.requests.CreateAccountRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
import com.example.mybank.enums.AccountType;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.AccountStatusException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.mappers.AccountMapper;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.AccountService;
import com.example.mybank.util.AccountNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final AccountNumberGenerator accountNumberGenerator;
    private final PasswordEncoder passwordEncoder;

    public AccountServiceImpl(UserRepository userRepository,
                              AccountRepository accountRepository,
                              AccountMapper accountMapper,
                              AccountNumberGenerator accountNumberGenerator,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.accountNumberGenerator = accountNumberGenerator;
        this.passwordEncoder = passwordEncoder;
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

    @Override
    @Transactional
    public AccountResponse updateAccountDetails(String email, String accountNumber, AccountUpdateInfoRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));

        if (request.phoneNumber() != null) {
            account.setPhoneNumber(request.phoneNumber());
        }

        Account updatedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(updatedAccount);
    }

    @Override
    @Transactional
    public AccountResponse updateAccountType(String accountNumber, AccountType type) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

        account.setType(type);

        Account updatedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(updatedAccount);
    }

    @Override
    @Transactional
    public AccountResponse createAccount(String email, CreateAccountRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        Account account = new Account(
                accountNumberGenerator.generate(),
                BigDecimal.ZERO,
                AccountStatus.PENDING_VERIFICATION,
                AccountTier.TIER_1_UNVERIFIED,
                request.type(),
                request.currency(),
                user,
                passwordEncoder.encode(request.pin())
        );

        Account savedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(savedAccount);
    }

    @Override
    @Transactional
    public void deleteAccount(String email, String accountNumber, String pin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));

        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }
        
        accountRepository.delete(account);
    }

    @Override
    @Transactional
    public AccountResponse transferAccountOwner(String email, String accountNumber, String newOwnerEmail, String pin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));
        User newOwner = userRepository.findByEmail(newOwnerEmail)
                .orElseThrow(() -> new UserNotFoundException("New owner email not found: " + newOwnerEmail));

        Account account = accountRepository.findByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));
        
        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }
        
        account.setUser(newOwner);
        Account savedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        return accountMapper.toAccountResponseList(accounts);
    }

    @Override
    @Transactional
    public AccountResponse freezeMyAccount(String email, String accountNumber, String reason) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User email not found: " + email));

        // Lock the row so a deposit or transfer can't run against the account while it is being frozen
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account " + accountNumber + " not found or does not belong to user"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Only active accounts can be frozen");
        }

        // Unfreezing stays admin-only, through updateAccountStatus
        account.setStatus(AccountStatus.FROZEN);
        Account frozenAccount = accountRepository.save(account);
        log.info("Account {} frozen by its owner, reason: {}", accountNumber, reason);
        return accountMapper.toAccountResponse(frozenAccount);
    }
}
