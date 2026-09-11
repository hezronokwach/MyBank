package com.example.mybank.mappers;

import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.entity.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
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
                account.getPhoneNumber(),
                account.getType(),
                account.getStatus(),
                account.getTier(),
                account.getCreatedAt(),
                calculateInterestInfo(account)
        );
    }

    private String calculateInterestInfo(Account account) {
        BigDecimal rate;
        switch (account.getType()) {
            case SAVINGS -> rate = new BigDecimal("0.07");
            case FIXED_01 -> rate = new BigDecimal("0.04");
            case FIXED_02 -> rate = new BigDecimal("0.05");
            case FIXED_03 -> rate = new BigDecimal("0.08");
            default -> {
                return "You will not get interests because the account is of type " + account.getType().name().toLowerCase();
            }
        }
        
        BigDecimal interest = account.getBalance().multiply(rate).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        return "You will get $" + interest + " as interest on day " + account.getCreatedAt().atZone(ZoneId.systemDefault()).getDayOfMonth() + " of every month";
    }

    // Maps a List of Account entities to a List of AccountResponse DTOs
    public List<AccountResponse> toAccountResponseList(List<Account> accounts) {
        return accounts.stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }
}