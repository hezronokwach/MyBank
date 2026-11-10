package com.example.mybank.services.impl;

import com.example.mybank.entity.Card;
import com.example.mybank.exceptions.CardPinLockedException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.repository.CardRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Checks the PIN for a card action (moving money, showing card details).
 * A card uses the PIN of the account it is linked to.
 *
 * After 3 wrong PINs in a row, card actions are locked for 15 minutes, even with the right PIN.
 *
 * The caller must be @Transactional with noRollbackFor = {UnauthorizedException.class, CardPinLockedException.class}.
 * Otherwise the error would also undo the saved count of wrong PINs, and the lock would never happen.
 * The caller must also pass a card it has locked (findAndLock...), so two requests can't count at the same time.
 */
@Component
public class CardPinChecker {

    static final int MAX_WRONG_PINS = 3;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private final CardRepository cardRepository;
    private final PasswordEncoder passwordEncoder;

    public CardPinChecker(CardRepository cardRepository, PasswordEncoder passwordEncoder) {
        this.cardRepository = cardRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void check(Card card, String pin) {
        // 1. Still locked from earlier wrong PINs? Don't even look at this PIN.
        if (card.getPinLockedUntil() != null && Instant.now().isBefore(card.getPinLockedUntil())) {
            throw new CardPinLockedException("Too many wrong PINs. Try again in 15 minutes.");
        }

        // 2. The right PIN starts the count again
        if (passwordEncoder.matches(pin, card.getAccount().getPin())) {
            card.setFailedPinAttempts(0);
            card.setPinLockedUntil(null);
            cardRepository.save(card);
            return;
        }

        // 3. A wrong PIN is counted; the 3rd one locks the card for 15 minutes
        int wrongPins = card.getFailedPinAttempts() + 1;
        if (wrongPins >= MAX_WRONG_PINS) {
            card.setFailedPinAttempts(0);
            card.setPinLockedUntil(Instant.now().plus(LOCK_TIME));
            cardRepository.save(card);
            throw new CardPinLockedException("Too many wrong PINs. Try again in 15 minutes.");
        }
        card.setFailedPinAttempts(wrongPins);
        cardRepository.save(card);
        throw new UnauthorizedException("Invalid PIN");
    }
}
