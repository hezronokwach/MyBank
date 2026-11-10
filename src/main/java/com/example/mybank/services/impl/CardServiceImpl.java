package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.CardFeeResponse;
import com.example.mybank.dto.responses.CardResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.AccountStatusException;
import com.example.mybank.exceptions.CardAlreadyExistsException;
import com.example.mybank.exceptions.CurrencyMatchException;
import com.example.mybank.exceptions.InsufficientBalanceException;
import com.example.mybank.exceptions.TransactionNotAllowedException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.CardService;
import com.example.mybank.util.ReferenceGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Virtual cards. Rules for every card action:
 *   - only CURRENT and SAVINGS accounts can be used (never FIXED_01, FIXED_02 or FIXED_03)
 *   - the account must be ACTIVE and belong to the customer
 *   - cards are in KES, so the account must be in KES too
 *   - a card uses the PIN of the account it is linked to
 *   - one card per customer (waiting for approval, active or frozen)
 */
@Service
public class CardServiceImpl implements CardService {

    static final String CARD_CURRENCY = "KES";

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ReferenceGenerator referenceGenerator;
    private final PasswordEncoder passwordEncoder;
    private final BigDecimal issueFee;

    public CardServiceImpl(CardRepository cardRepository,
                           AccountRepository accountRepository,
                           UserRepository userRepository,
                           TransactionRepository transactionRepository,
                           ReferenceGenerator referenceGenerator,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.cards.issue-fee:300}") BigDecimal issueFee) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.referenceGenerator = referenceGenerator;
        this.passwordEncoder = passwordEncoder;
        this.issueFee = issueFee;
    }

    /**
     * POST /api/v1/cards
     * Takes the fee from the account and saves the card as waiting for an admin.
     * All in one transaction: if anything fails, no money moves and no card is saved.
     */
    @Override
    @Transactional
    public CardResponse applyForCard(String email, String accountNumber, String pin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // 1. Only one card at a time
        List<CardStatus> openStatuses = new ArrayList<>();
        openStatuses.add(CardStatus.PENDING_APPROVAL);
        openStatuses.add(CardStatus.ACTIVE);
        openStatuses.add(CardStatus.FROZEN);
        if (cardRepository.existsByUserIdAndStatusIn(user.getId(), openStatuses)) {
            throw new CardAlreadyExistsException("You already have a card");
        }

        // 2. The account must be the customer's. It is locked so its balance can't change under us.
        //    Someone else's account gives the same answer as one that doesn't exist.
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        // 3. The PIN of that account
        if (!passwordEncoder.matches(pin, account.getPin())) {
            throw new UnauthorizedException("Invalid PIN");
        }

        // 4. Only current and savings accounts
        if (account.getType() != AccountType.CURRENT && account.getType() != AccountType.SAVINGS) {
            throw new TransactionNotAllowedException("Only a current or savings account can pay for a card");
        }

        // 5. Active, and in the card's currency
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account not active");
        }
        if (!CARD_CURRENCY.equals(account.getCurrency())) {
            throw new CurrencyMatchException("Cards are in KES, so the fee must be paid from a KES account");
        }

        // 6. Enough money (the balance must stay above 0, the same rule as withdrawals)
        if (account.getBalance().compareTo(issueFee) <= 0) {
            throw new InsufficientBalanceException("Insufficient balance for the card fee of KES " + issueFee.toPlainString());
        }

        // 7. Take the fee and record it in the account's history
        account.setBalance(account.getBalance().subtract(issueFee));
        accountRepository.save(account);
        Transaction fee = new Transaction(referenceGenerator.generateCardFee(), account, null,
                issueFee, TransactionType.CARD_FEE, TransactionStatus.SUCCESS);
        transactionRepository.save(fee);

        // 8. Save the card, waiting for an admin
        Card card = cardRepository.save(new Card(user, account, CARD_CURRENCY, issueFee));
        return toResponse(card);
    }

    /**
     * GET /api/v1/cards/me
     * The customer's cards, newest first (an empty list if they never applied).
     */
    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getMyCards(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<CardResponse> cards = new ArrayList<>();
        for (Card card : cardRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            cards.add(toResponse(card));
        }
        return cards;
    }

    // GET /api/v1/cards/fee
    @Override
    public CardFeeResponse getIssueFee() {
        return new CardFeeResponse(toMoney(issueFee), CARD_CURRENCY);
    }

    private CardResponse toResponse(Card card) {
        String accountNumber = card.getAccount().getAccountNumber();
        String maskedAccount = "..." + accountNumber.substring(accountNumber.length() - 4);
        return new CardResponse(card.getId(), card.getStatus(), card.getLast4(), toMoney(card.getBalance()),
                card.getCurrency(), card.getExpiryMonth(), card.getExpiryYear(), maskedAccount,
                toMoney(card.getFeePaid()), card.getRejectionReason(), card.getCreatedAt());
    }

    // 300.0000 from the database becomes 300.00
    private BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
