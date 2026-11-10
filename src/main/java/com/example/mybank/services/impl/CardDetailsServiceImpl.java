package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.CardDetailsResponse;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.CardDetailView;
import com.example.mybank.entity.User;
import com.example.mybank.enums.CardRevealOutcome;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardPinLockedException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.CardDetailViewRepository;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.CardDetailsService;
import com.example.mybank.util.CardEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shows a card's full number and CVV, but only after the PIN of the card's linked account.
 *
 * It uses the same lock as card moves: 3 wrong PINs in a row lock the card for 15 minutes.
 * Every attempt is recorded (who, which card, when, and how it ended), never the PIN or the details.
 * The number and CVV are never written to a log.
 */
@Service
public class CardDetailsServiceImpl implements CardDetailsService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardDetailViewRepository detailViewRepository;
    private final CardPinChecker pinChecker;
    private final CardEncryptor cardEncryptor;

    public CardDetailsServiceImpl(CardRepository cardRepository,
                                  UserRepository userRepository,
                                  CardDetailViewRepository detailViewRepository,
                                  CardPinChecker pinChecker,
                                  CardEncryptor cardEncryptor) {
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
        this.detailViewRepository = detailViewRepository;
        this.pinChecker = pinChecker;
        this.cardEncryptor = cardEncryptor;
    }

    /**
     * POST /api/v1/cards/{id}/details
     *
     * noRollbackFor: a wrong PIN or the lock must still save the wrong-PIN count and the record of the attempt.
     * Nothing else is changed before the PIN is checked, so nothing else can be kept by mistake.
     */
    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, CardPinLockedException.class})
    public CardDetailsResponse revealDetails(String email, UUID cardId, String pin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // 1. The customer's own card, locked so two attempts can't be counted at the same time.
        //    Someone else's card gives the same answer as a card that doesn't exist.
        Card card = cardRepository.findAndLockByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));

        // 2. A card waiting for approval, or rejected, has no number yet
        if (card.getCardNumberEncrypted() == null) {
            throw new CardStatusConflictException("This card has not been issued yet");
        }

        // 3. The PIN. Whatever happens, the attempt is recorded.
        try {
            pinChecker.check(card, pin);
        } catch (CardPinLockedException e) {
            record(card, user, CardRevealOutcome.LOCKED);
            throw e;
        } catch (UnauthorizedException e) {
            record(card, user, CardRevealOutcome.WRONG_PIN);
            throw e;
        }

        // 4. The right PIN: decrypt and show
        record(card, user, CardRevealOutcome.SHOWN);
        String cardNumber = cardEncryptor.decrypt(card.getCardNumberEncrypted());
        String cvv = cardEncryptor.decrypt(card.getCvvEncrypted());
        return new CardDetailsResponse(cardNumber, cvv, card.getExpiryMonth(), card.getExpiryYear(), card.getLast4());
    }

    private void record(Card card, User user, CardRevealOutcome outcome) {
        detailViewRepository.save(new CardDetailView(card.getId(), user.getId(), outcome));
    }
}
