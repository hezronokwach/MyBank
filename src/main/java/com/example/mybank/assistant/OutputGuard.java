package com.example.mybank.assistant;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans every assistant reply before it is saved or shown to the customer.
 * The model should never output these, but if it does, they are caught here:
 *
 *   1. account numbers: any run of 8 or more digits becomes "..." plus its last 4 digits
 *   2. PINs: "PIN" followed by 4 digits keeps the word but the digits become ****
 *   3. login tokens (JWTs): removed completely
 *   4. card CVVs: "CVV" followed by 3 digits keeps the word but the digits become ***
 *
 * Card numbers are 16 digits, so rule 1 already hides them.
 *
 * Normal text is left alone: amounts such as 2,000 or 13,000.00 and dates such as 2026-11-05
 * never have 8 digits in a row, so they are not touched.
 */
@Component
public class OutputGuard {

    static final int ACCOUNT_NUMBER_MIN_DIGITS = 8;
    static final String REMOVED_TOKEN = "[removed]";

    // "PIN", then up to 10 characters that are not digits (such as " is " or ": "), then exactly 4 digits.
    // Group 1 is everything before the digits, group 2 is the 4 digits.
    // (?i) = ignore upper/lower case, \b = start or end of a word, \D = not a digit, \d = a digit,
    // (?!\d) = the next character must not be a digit (so "PIN 123456" isn't cut in the middle)
    private static final Pattern PIN = Pattern.compile("(?i)(\\bPIN\\b\\D{0,10}?)(\\d{4})(?!\\d)");

    // "CVV" (or "CVC"), then up to 10 characters that are not digits, then exactly 3 digits
    private static final Pattern CVV = Pattern.compile("(?i)(\\b(?:CVV|CVC)\\b\\D{0,10}?)(\\d{3})(?!\\d)");

    // A JWT is three long base64 parts joined by dots, such as eyJhbGci....eyJzdWIi....SflKxwRJ...
    // [A-Za-z0-9_-] = one base64 character, {10,} = at least 10 of them
    private static final Pattern JWT = Pattern.compile("[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");

    public String clean(String text) {
        if (text == null) {
            return null;
        }

        String cleaned = removeTokens(text);
        cleaned = maskPins(cleaned);
        cleaned = maskCvvs(cleaned);
        cleaned = maskAccountNumbers(cleaned);
        return cleaned;
    }

    // 1. "Your account 1000004821" becomes "Your account ...4821"
    String maskAccountNumbers(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder digits = new StringBuilder(); // the run of digits we are in the middle of

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                result.append(maskIfLong(digits.toString()));
                digits.setLength(0);
                result.append(c);
            }
        }
        // The text may end with digits
        result.append(maskIfLong(digits.toString()));

        return result.toString();
    }

    private String maskIfLong(String digits) {
        if (digits.length() >= ACCOUNT_NUMBER_MIN_DIGITS) {
            return "..." + digits.substring(digits.length() - 4);
        }
        return digits;
    }

    // 2. "your PIN is 1234" becomes "your PIN is ****"
    String maskPins(String text) {
        Matcher matcher = PIN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            // Keep group 1 ("PIN is "), replace the digits
            String replacement = matcher.group(1) + "****";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // 4. "the CVV is 042" becomes "the CVV is ***"
    String maskCvvs(String text) {
        Matcher matcher = CVV.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "***";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // 3. Any login token is taken out
    String removeTokens(String text) {
        Matcher matcher = JWT.matcher(text);
        return matcher.replaceAll(REMOVED_TOKEN);
    }
}
