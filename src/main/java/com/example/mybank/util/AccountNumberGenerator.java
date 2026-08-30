package com.example.mybank.util;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class AccountNumberGenerator {

    private final Random random = new Random();

    /**
     * Generates a unique 10-digit bank account number starting with a fixed prefix.
     * Example output: "1009823471"
     */
    public String generate() {
        // Standard bank branch/type prefix (e.g., 100)
        String prefix = "100";

        // Generate remaining 7 random digits
        int number = random.nextInt(9000000) + 1000000;

        return prefix + number;
    }
}
