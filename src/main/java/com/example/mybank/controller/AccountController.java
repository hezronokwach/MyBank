package com.example.mybank.controller;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.requests.DepositRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.dto.responses.TransactionResponse;
import com.example.mybank.services.AccountService;
import com.example.mybank.services.TransactionService;
import jakarta.validation.Valid;
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

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
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

    @PatchMapping("/{accountNumber}/deposits")
    @PreAuthorize("hasRole('USER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest request,
            Authentication authentication
    ){
        String userEmail = authentication.getName();
        TransactionResponse transactionResponse = transactionService.deposit(userEmail,accountNumber, request.amount());
        return ResponseEntity.ok(transactionResponse);
    }
}