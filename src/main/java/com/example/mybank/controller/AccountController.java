package com.example.mybank.controller;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.services.AccountService;
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

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
}