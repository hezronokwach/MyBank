package com.example.mybank.assistant.tools;

import com.example.mybank.dto.responses.AccountResponse;
import com.example.mybank.services.AccountService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what the customer typed ("4821" or a full number) into one of THEIR accounts.
 * It only ever searches the logged-in customer's own accounts.
 */
@Component
public class AccountResolver {

    public enum Status {
        FOUND,
        NOT_FOUND,
        AMBIGUOUS // More than one of the customer's accounts ends with these digits
    }

    public record Resolution(Status status, AccountResponse account, List<String> maskedOptions) {}

    private final AccountService accountService;

    public AccountResolver(AccountService accountService) {
        this.accountService = accountService;
    }

    public Resolution resolve(String email, String accountNumberOrLast4) {
        // Allow "1000-004-821" or "1000 004 821" by removing spaces, dots and dashes
        String input = "";
        if (accountNumberOrLast4 != null) {
            input = accountNumberOrLast4.replace(" ", "").replace(".", "").replace("-", "");
        }

        // Need at least 4 digits, and digits only
        if (input.length() < 4 || !isAllDigits(input)) {
            return new Resolution(Status.NOT_FOUND, null, new ArrayList<>());
        }

        // Look only through this customer's accounts
        List<AccountResponse> matches = new ArrayList<>();
        for (AccountResponse account : accountService.getMyAccounts(email)) {
            if (account.accountNumber().endsWith(input)) {
                matches.add(account);
            }
        }

        if (matches.isEmpty()) {
            return new Resolution(Status.NOT_FOUND, null, new ArrayList<>());
        }

        if (matches.size() > 1) {
            List<String> maskedOptions = new ArrayList<>();
            for (AccountResponse account : matches) {
                maskedOptions.add(mask(account.accountNumber()));
            }
            return new Resolution(Status.AMBIGUOUS, null, maskedOptions);
        }

        return new Resolution(Status.FOUND, matches.get(0), new ArrayList<>());
    }

    // "1000004821" becomes "...4821"
    public static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        if (accountNumber.length() <= 4) {
            return "..." + accountNumber;
        }
        return "..." + accountNumber.substring(accountNumber.length() - 4);
    }

    private boolean isAllDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
