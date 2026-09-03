package com.example.mybank.mappers;

import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.entity.Account;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AccountMapper {

    // Maps a single Account entity to AccountResponse DTO
    public AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getType(),
                account.getStatus(),
                account.getTier(),
                account.getCreatedAt()
        );
    }

    // Maps a List of Account entities to a List of AccountResponse DTOs
    public List<AccountResponse> toAccountResponseList(List<Account> accounts) {
        return accounts.stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

}