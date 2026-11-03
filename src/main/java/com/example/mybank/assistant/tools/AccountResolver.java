package com.example.mybank.assistant.tools;

import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.services.AccountService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns what the customer typed ("4821" or a full number) into one of THEIR accounts.
 * It only ever searches the logged-in customer's own accounts.
 */
@Component
public class AccountResolver {

    public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }

    public record Resolution(Status status, AccountResponse account, List<String> maskedOptions) {}

    private final AccountService accountService;

    public AccountResolver(AccountService accountService) {
        this.accountService = accountService;
    }

    public Resolution resolve(String email, String accountNumberOrLast4) {
        String input = accountNumberOrLast4 == null ? "" : accountNumberOrLast4.replaceAll("[\\s.\\-]", "");
        if (!input.matches("\\d{4,}")) {
            return new Resolution(Status.NOT_FOUND, null, List.of());
        }

        List<AccountResponse> matches = accountService.getMyAccounts(email).stream()
                .filter(account -> account.accountNumber().equals(input) || account.accountNumber().endsWith(input))
                .toList();

        if (matches.isEmpty()) {
            return new Resolution(Status.NOT_FOUND, null, List.of());
        }
        if (matches.size() > 1) {
            return new Resolution(Status.AMBIGUOUS, null,
                    matches.stream().map(account -> mask(account.accountNumber())).toList());
        }
        return new Resolution(Status.FOUND, matches.get(0), List.of());
    }

    public static String mask(String accountNumber) {
        return accountNumber == null ? null : "..." + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}
