package com.example.mybank.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Makes card numbers and CVVs for MyBank's virtual cards.
 *
 * Card numbers start with 999, a made-up prefix that no real card network uses, so a MyBank
 * demo card can never be mistaken for a real Visa or Mastercard. The last digit is a Luhn
 * check digit, the same kind of check real cards use to catch typing mistakes.
 */
@Component
public class CardNumberGenerator {

    static final String PREFIX = "999";
    static final int CARD_NUMBER_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    // 16 digits: "999", 12 random digits, then the check digit
    public String generateCardNumber() {
        StringBuilder number = new StringBuilder(PREFIX);
        while (number.length() < CARD_NUMBER_LENGTH - 1) {
            number.append(random.nextInt(10));
        }
        number.append(luhnCheckDigit(number.toString()));
        return number.toString();
    }

    // 3 random digits, for example "042"
    public String generateCvv() {
        int cvv = random.nextInt(1000);
        return String.format("%03d", cvv);
    }

    /**
     * The Luhn check digit for the digits so far.
     * From the right, every second digit is doubled (and 9 taken off if it goes over 9),
     * all digits are added up, and the check digit is what brings the total to a multiple of 10.
     */
    static int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubleIt = true; // the digit next to the check digit is doubled
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit = digit * 2;
                if (digit > 9) {
                    digit = digit - 9;
                }
            }
            sum = sum + digit;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    // True if the whole number, check digit included, passes the Luhn check
    public static boolean passesLuhn(String number) {
        String withoutCheck = number.substring(0, number.length() - 1);
        int check = number.charAt(number.length() - 1) - '0';
        return luhnCheckDigit(withoutCheck) == check;
    }
}
