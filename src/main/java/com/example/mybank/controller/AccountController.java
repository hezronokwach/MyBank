package com.example.mybank.controller;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.requests.AccountUpdateInfoRequest;
import com.example.mybank.dto.requests.CreateAccountRequest;
import com.example.mybank.dto.requests.PinRequest;
import com.example.mybank.dto.requests.TransactionRequest;
import com.example.mybank.dto.requests.TransferOwnershipRequest;
import com.example.mybank.dto.requests.TransferRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.enums.AccountType;
import com.example.mybank.services.AccountService;
import com.example.mybank.services.TransactionService;
import com.example.mybank.services.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final TransferService transferService;

    public AccountController(AccountService accountService, TransactionService transactionService, TransferService transferService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.transferService = transferService;
    }

    /**
     * GET /api/v1/accounts/me
     * Fetch all bank accounts owned by the currently authenticated user.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(Authentication authentication) {
        String userEmail = authentication.getName();
        List<AccountResponse> accounts = accountService.getMyAccounts(userEmail);
        return ResponseEntity.ok(accounts);
    }

    /**
     * GET /api/v1/accounts
     * Admin endpoint to list all accounts.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            Authentication authentication,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        String userEmail = authentication.getName();
        AccountResponse account = accountService.createAccount(userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    /**
     * GET /api/v1/accounts/{accountNumber}
     * Fetch a specific bank account owned by the authenticated user.
     */
    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountResponse> getAccountByNumber(
            @PathVariable String accountNumber,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        AccountResponse account = accountService.getAccountByNumber(userEmail, accountNumber);
        return ResponseEntity.ok(account);
    }

    /**
     * PATCH /api/v1/accounts/{accountNumber}/status
     * Admin/Compliance endpoint to update account status (e.g. PENDING_VERIFICATION -> ACTIVE) and tier.
     */
    @PatchMapping("/{accountNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable String accountNumber,
            @Valid @RequestBody AccountStatusUpdateRequest request
    ) {
        AccountResponse updatedAccount = accountService.updateAccountStatus(accountNumber, request);
        return ResponseEntity.ok(updatedAccount);
    }

    @PatchMapping("/{accountNumber}/type")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> updateAccountType(
            @PathVariable String accountNumber,
            @RequestBody AccountType type
    ) {
        AccountResponse updatedAccount = accountService.updateAccountType(accountNumber, type);
        return ResponseEntity.ok(updatedAccount);
    }

    @PatchMapping("/{accountNumber}/details")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountResponse> updateAccountDetails(
            @PathVariable String accountNumber,
            @Valid @RequestBody AccountUpdateInfoRequest request,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        AccountResponse updatedAccount = accountService.updateAccountDetails(userEmail, accountNumber, request);
        return ResponseEntity.ok(updatedAccount);
    }

    @DeleteMapping("/{accountNumber}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable String accountNumber,
            @Valid @RequestBody PinRequest request,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        accountService.deleteAccount(userEmail, accountNumber, request.pin());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{accountNumber}/transfer")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountResponse> transferAccountOwner(
            @PathVariable String accountNumber,
            @Valid @RequestBody TransferOwnershipRequest request,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        AccountResponse updatedAccount = accountService.transferAccountOwner(userEmail, accountNumber, request.newOwnerEmail(), request.pin());
        return ResponseEntity.ok(updatedAccount);
    }

    @PatchMapping("/{accountNumber}/deposits")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication
    ){
        String userEmail = authentication.getName();
        TransactionResponse transactionResponse = transactionService.deposit(userEmail, accountNumber, request.amount(), request.pin());
        return ResponseEntity.ok(transactionResponse);
    }

    @PatchMapping("/{accountNumber}/withdrawals")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable String accountNumber,
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication
    ){
        String userEmail = authentication.getName();
        TransactionResponse transactionResponse = transactionService.withdraw(userEmail, accountNumber, request.amount(), request.pin());
        return ResponseEntity.ok(transactionResponse);
    }

    @PatchMapping("/transfers")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> transfers(
            @RequestHeader("Idempotency-key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ){
        String userEmail = authentication.getName();
        TransactionResponse transactionResponse = transferService.transfer(
                userEmail,
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount(),
                idempotencyKey,
                request.pin());
        return ResponseEntity.ok(transactionResponse);
    }

}