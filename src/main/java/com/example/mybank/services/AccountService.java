package com.example.mybank.services;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.enums.AccountType;

import java.util.List;

public interface AccountService {
    List<AccountResponse> getMyAccounts(String email);
    AccountResponse getAccountByNumber(String email, String accountNumber);
    AccountResponse updateAccountStatus(String accountNumber, AccountStatusUpdateRequest request);
    AccountResponse updateAccountType(String accountNumber, AccountType type);
    List<AccountResponse> getAllAccounts();
}