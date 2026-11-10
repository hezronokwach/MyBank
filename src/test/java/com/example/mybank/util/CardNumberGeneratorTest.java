package com.example.mybank.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardNumberGeneratorTest {

    private final CardNumberGenerator generator = new CardNumberGenerator();

    @Test
    void cardNumbersAre16DigitsStartWith999AndPassTheLuhnCheck() {
        for (int i = 0; i < 200; i++) {
            String number = generator.generateCardNumber();

            assertThat(number).hasSize(16).startsWith("999").containsOnlyDigits();
            assertThat(CardNumberGenerator.passesLuhn(number)).isTrue();
        }
    }

    @Test
    void theLuhnCheckMatchesAKnownTestNumberAndCatchesATypo() {
        // A well-known test card number that passes the Luhn check
        assertThat(CardNumberGenerator.passesLuhn("4539578763621486")).isTrue();
        // The same number with one digit changed
        assertThat(CardNumberGenerator.passesLuhn("4539578763621487")).isFalse();
    }

    @Test
    void cvvsAreAlways3Digits() {
        for (int i = 0; i < 200; i++) {
            assertThat(generator.generateCvv()).hasSize(3).containsOnlyDigits();
        }
    }
}
