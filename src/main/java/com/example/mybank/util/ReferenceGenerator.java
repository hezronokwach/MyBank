package com.example.mybank.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ReferenceGenerator {

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a unique 10-digit bank account number starting with a fixed prefix.
     * Example output: "1009823471"
     */
    public String generateDeposit() {
        // Standard bank branch/type prefix (e.g., DEP)
        String prefix = "DEP";

        // Generate remaining 7 random digits
        int number = random.nextInt(9000000) + 1000000;

        return prefix + number;
    }

    public String generateWithdrawal() {
        // Standard bank branch/type prefix (e.g., DEP)
        String prefix = "WDR";

        // Generate remaining 7 random digits
        int number = random.nextInt(9000000) + 1000000;

        return prefix + number;
    }
}
