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

    // The card fee: FEE followed by 7 random digits
    public String generateCardFee() {
        String prefix = "FEE";
        int number = random.nextInt(9000000) + 1000000;
        return prefix + number;
    }

    // A refunded card fee: RFD followed by 7 random digits
    public String generateCardRefund() {
        String prefix = "RFD";
        int number = random.nextInt(9000000) + 1000000;
        return prefix + number;
    }

    // Money moved onto a card: CTU followed by 7 random digits
    public String generateCardTopUp() {
        String prefix = "CTU";
        int number = random.nextInt(9000000) + 1000000;
        return prefix + number;
    }

    // Money moved off a card: CWD followed by 7 random digits
    public String generateCardWithdrawal() {
        String prefix = "CWD";
        int number = random.nextInt(9000000) + 1000000;
        return prefix + number;
    }

    public String generateTransfer() {
        // Standard bank branch/type prefix (e.g., DEP)
        String prefix = "TRN";

        // Generate remaining 7 random digits
        int number = random.nextInt(9000000) + 1000000;

        return prefix + number;
    }
}
