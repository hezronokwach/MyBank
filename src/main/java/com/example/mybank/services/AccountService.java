package com.example.mybank.services;

import com.example.mybank.dto.requests.AccountStatusUpdateRequest;
import com.example.mybank.dto.requests.AccountUpdateInfoRequest;
import com.example.mybank.dto.requests.CreateAccountRequest;
import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.enums.AccountType;

import java.util.List;

public interface AccountService {
    List<AccountResponse> getMyAccounts(String email);
    AccountResponse createAccount(String email, CreateAccountRequest request);
    AccountResponse getAccountByNumber(String email, String accountNumber);
    AccountResponse updateAccountStatus(String accountNumber, AccountStatusUpdateRequest request);
    AccountResponse updateAccountDetails(String email, String accountNumber, AccountUpdateInfoRequest request);
    AccountResponse updateAccountType(String accountNumber, AccountType type);
    void deleteAccount(String email, String accountNumber, String pin);
    AccountResponse transferAccountOwner(String email, String accountNumber, String newOwnerEmail, String pin);
    List<AccountResponse> getAllAccounts();
}