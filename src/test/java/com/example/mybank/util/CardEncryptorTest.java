package com.example.mybank.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardEncryptorTest {

    // Base64 of the 32 bytes "0123456789abcdef0123456789abcdef" (a test key only)
    static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    // Base64 of the 32 bytes "fedcba9876543210fedcba9876543210"
    static final String OTHER_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private final CardEncryptor encryptor = new CardEncryptor(TEST_KEY);

    @Test
    void encryptsAndDecryptsBack() {
        String saved = encryptor.encrypt("9991234567890123");

        assertThat(saved).doesNotContain("9991234567890123").doesNotContain("0123");
        assertThat(encryptor.decrypt(saved)).isEqualTo("9991234567890123");
    }

    @Test
    void theSameNumberLooksDifferentEveryTime() {
        assertThat(encryptor.encrypt("123")).isNotEqualTo(encryptor.encrypt("123"));
    }

    @Test
    void anotherKeyCannotReadIt() {
        String saved = encryptor.encrypt("9991234567890123");

        assertThatThrownBy(() -> new CardEncryptor(OTHER_KEY).decrypt(saved))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changedTextIsDetected() {
        String saved = encryptor.encrypt("9991234567890123");
        // Change one character in the middle
        char[] chars = saved.toCharArray();
        int middle = chars.length / 2;
        if (chars[middle] == 'A') {
            chars[middle] = 'B';
        } else {
            chars[middle] = 'A';
        }
        String changed = new String(chars);

        assertThatThrownBy(() -> encryptor.decrypt(changed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAKeyItSaysSoClearly() {
        CardEncryptor noKey = new CardEncryptor("");

        assertThatThrownBy(() -> noKey.encrypt("123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CARD_ENCRYPTION_KEY is not set, so cards can't be issued or shown");
    }

    @Test
    void aKeyOfTheWrongLengthIsRefused() {
        CardEncryptor shortKey = new CardEncryptor("c2hvcnQ="); // "short"

        assertThatThrownBy(() -> shortKey.encrypt("123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CARD_ENCRYPTION_KEY must be 32 bytes, Base64-encoded");
    }
}
