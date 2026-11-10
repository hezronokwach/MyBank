package com.example.mybank.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit test: no Spring, no database
class OutputGuardTest {

    private final OutputGuard guard = new OutputGuard();

    @Test
    void masksA16DigitCardNumber() {
        // Card numbers are 16 digits, so the account number rule already hides them
        assertThat(guard.clean("Your card is 9991234567890123."))
                .isEqualTo("Your card is ...0123.");
    }

    @Test
    void masksACvv() {
        assertThat(guard.clean("The CVV is 042.")).isEqualTo("The CVV is ***.");
        assertThat(guard.clean("cvv: 917")).isEqualTo("cvv: ***");
        assertThat(guard.clean("Your CVC 305 is secret")).isEqualTo("Your CVC *** is secret");
    }

    @Test
    void leavesOtherThreeDigitNumbersAlone() {
        assertThat(guard.clean("You sent KES 500 on 3 Nov.")).isEqualTo("You sent KES 500 on 3 Nov.");
    }

    @Test
    void masksAFullAccountNumber() {
        assertThat(guard.clean("Your current account 1000004821 has KES 3,300.00."))
                .isEqualTo("Your current account ...4821 has KES 3,300.00.");
    }

    @Test
    void masksAccountNumbersAtTheStartAndEndOfTheText() {
        assertThat(guard.clean("1000004821")).isEqualTo("...4821");
        assertThat(guard.clean("Sent to 1000007777")).isEqualTo("Sent to ...7777");
        assertThat(guard.clean("12345678 and 1000004821")).isEqualTo("...5678 and ...4821");
    }

    @Test
    void leavesAlreadyMaskedNumbersAlone() {
        assertThat(guard.clean("Account ...4821")).isEqualTo("Account ...4821");
    }

    @Test
    void masksAPinMention() {
        assertThat(guard.clean("Your PIN is 1234")).isEqualTo("Your PIN is ****");
        assertThat(guard.clean("pin: 9876, please keep it safe")).isEqualTo("pin: ****, please keep it safe");
        assertThat(guard.clean("PIN 0000")).isEqualTo("PIN ****");
    }

    @Test
    void doesNotTreatOtherWordsAsPin() {
        // "spinning" contains "pin" but is not the word PIN
        assertThat(guard.clean("The spinning wheel shows 1234 users")).isEqualTo("The spinning wheel shows 1234 users");
    }

    @Test
    void removesAJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ3YW5qaWt1QHRlc3QubG9jYWwifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        assertThat(guard.clean("Your token is " + jwt + " ok"))
                .isEqualTo("Your token is [removed] ok");
    }

    @Test
    void leavesNormalTextUnchanged() {
        String text = "On 2026-11-05T01:25+03:00 you sent KES 2,000 (ref TRN1234567). "
                + "Your savings balance is 13,000.00 and you have 2 accounts. Interest is 7% a year.";

        assertThat(guard.clean(text)).isEqualTo(text);
    }

    @Test
    void handlesEmptyAndMissingText() {
        assertThat(guard.clean("")).isEqualTo("");
        assertThat(guard.clean(null)).isNull();
    }
}
