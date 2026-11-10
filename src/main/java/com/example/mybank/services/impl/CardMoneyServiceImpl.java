package com.example.mybank.services.impl;

import com.example.mybank.dto.responses.CardMoveResponse;
import com.example.mybank.dto.responses.CardTransactionResponse;
import com.example.mybank.entity.Account;
import com.example.mybank.entity.Card;
import com.example.mybank.entity.IdempotencyKey;
import com.example.mybank.entity.Transaction;
import com.example.mybank.entity.User;
import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.enums.TransactionDirection;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import com.example.mybank.exceptions.AccountNotFoundException;
import com.example.mybank.exceptions.AccountStatusException;
import com.example.mybank.exceptions.AmountException;
import com.example.mybank.exceptions.CardNotFoundException;
import com.example.mybank.exceptions.CardPinLockedException;
import com.example.mybank.exceptions.CardStatusConflictException;
import com.example.mybank.exceptions.CurrencyMatchException;
import com.example.mybank.exceptions.InsufficientBalanceException;
import com.example.mybank.exceptions.TransactionNotAllowedException;
import com.example.mybank.exceptions.UnauthorizedException;
import com.example.mybank.exceptions.UserNotFoundException;
import com.example.mybank.repository.AccountRepository;
import com.example.mybank.repository.CardRepository;
import com.example.mybank.repository.IdempotencyRepository;
import com.example.mybank.repository.TransactionRepository;
import com.example.mybank.repository.UserRepository;
import com.example.mybank.services.CardMoneyService;
import com.example.mybank.util.ReferenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves money between an account and a card, like NCBA Loop.
 *
 * Rules for both directions:
 *   - the card must be ACTIVE and belong to the customer
 *   - the account must be CURRENT or SAVINGS (never FIXED_*), ACTIVE, the customer's own, in the card's currency
 *   - the PIN of the card's linked account, checked every time (3 wrong PINs lock the card for 15 minutes)
 *   - an idempotency key, so a double click moves the money only once
 *
 * Order inside each move: lock the card, check the PIN, then lock the account and move the money.
 * The PIN is always checked before any money moves. That matters because of noRollbackFor:
 * a wrong PIN keeps its saved count of wrong attempts, and there is nothing else to keep.
 */
@Service
public class CardMoneyServiceImpl implements CardMoneyService {

    static final int MAX_HISTORY = 50;

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ReferenceGenerator referenceGenerator;
    private final CardPinChecker pinChecker;
    private final ObjectMapper objectMapper;

    public CardMoneyServiceImpl(CardRepository cardRepository,
                                AccountRepository accountRepository,
                                UserRepository userRepository,
                                TransactionRepository transactionRepository,
                                IdempotencyRepository idempotencyRepository,
                                ReferenceGenerator referenceGenerator,
                                CardPinChecker pinChecker,
                                ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.referenceGenerator = referenceGenerator;
        this.pinChecker = pinChecker;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/v1/cards/{id}/top-up: account → card
     */
    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, CardPinLockedException.class})
    public CardMoveResponse topUp(String email, UUID cardId, String fromAccountNumber, BigDecimal amount,
                                  String idempotencyKey, String pin) {
        User user = loadUser(email);

        // Seen this key before? Give back the first answer and move nothing.
        String key = savedKey(user, idempotencyKey);
        CardMoveResponse earlier = earlierAnswer(key);
        if (earlier != null) {
            return earlier;
        }

        Card card = lockActiveCard(cardId, user);
        pinChecker.check(card, pin);

        checkAmount(amount);
        Account account = lockUsableAccount(fromAccountNumber, user, card);
        // The account must keep more than 0, the same rule as withdrawals
        if (account.getBalance().compareTo(amount) <= 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(amount));
        card.setBalance(card.getBalance().add(amount));
        accountRepository.save(account);
        cardRepository.save(card);
        Transaction move = transactionRepository.save(new Transaction(referenceGenerator.generateCardTopUp(),
                account, null, card, amount, TransactionType.CARD_TOPUP, TransactionStatus.SUCCESS));

        return remember(key, toMoveResponse(move, account, card));
    }

    /**
     * POST /api/v1/cards/{id}/withdraw: card → account
     */
    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, CardPinLockedException.class})
    public CardMoveResponse withdraw(String email, UUID cardId, String toAccountNumber, BigDecimal amount,
                                     String idempotencyKey, String pin) {
        User user = loadUser(email);

        String key = savedKey(user, idempotencyKey);
        CardMoveResponse earlier = earlierAnswer(key);
        if (earlier != null) {
            return earlier;
        }

        Card card = lockActiveCard(cardId, user);
        pinChecker.check(card, pin);

        checkAmount(amount);
        // The card can be emptied completely
        if (card.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Not enough money on the card");
        }
        Account account = lockUsableAccount(toAccountNumber, user, card);

        card.setBalance(card.getBalance().subtract(amount));
        account.setBalance(account.getBalance().add(amount));
        cardRepository.save(card);
        accountRepository.save(account);
        Transaction move = transactionRepository.save(new Transaction(referenceGenerator.generateCardWithdrawal(),
                null, account, card, amount, TransactionType.CARD_WITHDRAWAL, TransactionStatus.SUCCESS));

        return remember(key, toMoveResponse(move, account, card));
    }

    /**
     * GET /api/v1/cards/{id}/transactions?limit=10
     */
    @Override
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getCardTransactions(String email, UUID cardId, int limit) {
        User user = loadUser(email);
        Card card = cardRepository.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));

        int pageSize = limit;
        if (pageSize < 1) {
            pageSize = 1;
        }
        if (pageSize > MAX_HISTORY) {
            pageSize = MAX_HISTORY;
        }

        List<CardTransactionResponse> history = new ArrayList<>();
        for (Transaction t : transactionRepository.findRecentForCard(card.getId(), PageRequest.of(0, pageSize))) {
            TransactionDirection direction;
            Account account;
            if (t.getType() == TransactionType.CARD_TOPUP) {
                direction = TransactionDirection.IN;   // onto the card
                account = t.getFromAccount();
            } else {
                direction = TransactionDirection.OUT;  // off the card
                account = t.getToAccount();
            }
            history.add(new CardTransactionResponse(t.getCreatedAt(), t.getType(), direction,
                    toMoney(t.getAmount()), mask(account.getAccountNumber()), t.getReference()));
        }
        return history;
    }

    // ---- steps shared by both directions

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    // Someone else's card gives the same answer as a card that doesn't exist
    private Card lockActiveCard(UUID cardId, User user) {
        Card card = cardRepository.findAndLockByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardStatusConflictException("This card is not active");
        }
        return card;
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new AmountException("Amount must be more than 0");
        }
    }

    // The account on the other side: the customer's own, CURRENT or SAVINGS, ACTIVE, same currency as the card
    private Account lockUsableAccount(String accountNumber, User user, Card card) {
        Account account = accountRepository.findAndLockByAccountNumberAndUserId(accountNumber, user.getId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        if (account.getType() != AccountType.CURRENT && account.getType() != AccountType.SAVINGS) {
            throw new TransactionNotAllowedException("Only a current or savings account can be used with a card");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Account not active");
        }
        if (!account.getCurrency().equals(card.getCurrency())) {
            throw new CurrencyMatchException("Currencies do not match");
        }
        return account;
    }

    // Keys are kept per customer, so two customers can never collide on the same key
    private String savedKey(User user, String idempotencyKey) {
        return "card:" + user.getId() + ":" + idempotencyKey;
    }

    private CardMoveResponse earlierAnswer(String key) {
        Optional<IdempotencyKey> saved = idempotencyRepository.findByIdempotencyKey(key);
        if (saved.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(saved.get().getResponseBody(), CardMoveResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Error reading the saved answer for this request", e);
        }
    }

    private CardMoveResponse remember(String key, CardMoveResponse response) {
        try {
            IdempotencyKey record = new IdempotencyKey();
            record.setIdempotencyKey(key);
            record.setResponseBody(objectMapper.writeValueAsString(response));
            idempotencyRepository.save(record);
        } catch (Exception e) {
            throw new RuntimeException("Error saving the answer for this request", e);
        }
        return response;
    }

    private CardMoveResponse toMoveResponse(Transaction move, Account account, Card card) {
        return new CardMoveResponse(move.getReference(), move.getType(), toMoney(move.getAmount()),
                card.getCurrency(), mask(account.getAccountNumber()), toMoney(card.getBalance()), move.getCreatedAt());
    }

    private String mask(String accountNumber) {
        return "..." + accountNumber.substring(accountNumber.length() - 4);
    }

    private BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
