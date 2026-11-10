package com.example.mybank.controller;

import com.example.mybank.dto.requests.CardApplicationRequest;
import com.example.mybank.dto.requests.CardDetailsRequest;
import com.example.mybank.dto.requests.CardTopUpRequest;
import com.example.mybank.dto.requests.CardWithdrawRequest;
import com.example.mybank.dto.responses.CardDetailsResponse;
import com.example.mybank.dto.responses.CardFeeResponse;
import com.example.mybank.dto.responses.CardMoveResponse;
import com.example.mybank.dto.responses.CardResponse;
import com.example.mybank.dto.responses.CardTransactionResponse;
import com.example.mybank.services.CardDetailsService;
import com.example.mybank.services.CardMoneyService;
import com.example.mybank.services.CardService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;
    private final CardMoneyService cardMoneyService;
    private final CardDetailsService cardDetailsService;

    public CardController(CardService cardService, CardMoneyService cardMoneyService,
                          CardDetailsService cardDetailsService) {
        this.cardService = cardService;
        this.cardMoneyService = cardMoneyService;
        this.cardDetailsService = cardDetailsService;
    }

    /**
     * POST /api/v1/cards
     * Apply for a virtual card. The fee is taken from the account straight away; an admin then approves the card.
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardResponse> applyForCard(
            @Valid @RequestBody CardApplicationRequest request,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        CardResponse card = cardService.applyForCard(userEmail, request.accountNumber(), request.pin());
        return ResponseEntity.status(HttpStatus.CREATED).body(card);
    }

    /**
     * GET /api/v1/cards/fee
     * What a new card costs (from app.cards.issue-fee), shown before the customer applies.
     */
    @GetMapping("/fee")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardFeeResponse> getIssueFee() {
        return ResponseEntity.ok(cardService.getIssueFee());
    }

    /**
     * GET /api/v1/cards/me
     * The customer's cards, newest first. Only the last 4 digits, never the full number or CVV.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CardResponse>> getMyCards(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(cardService.getMyCards(userEmail));
    }

    /**
     * POST /api/v1/cards/{id}/top-up
     * Move money from one of the customer's accounts onto the card. Needs the PIN and an Idempotency-Key header.
     */
    @PostMapping("/{id}/top-up")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardMoveResponse> topUp(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CardTopUpRequest request,
            Authentication authentication
    ) {
        CardMoveResponse move = cardMoneyService.topUp(authentication.getName(), id, request.fromAccountNumber(),
                request.amount(), idempotencyKey, request.pin());
        return ResponseEntity.ok(move);
    }

    /**
     * POST /api/v1/cards/{id}/withdraw
     * Move money from the card back to one of the customer's accounts. Needs the PIN and an Idempotency-Key header.
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardMoveResponse> withdraw(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CardWithdrawRequest request,
            Authentication authentication
    ) {
        CardMoveResponse move = cardMoneyService.withdraw(authentication.getName(), id, request.toAccountNumber(),
                request.amount(), idempotencyKey, request.pin());
        return ResponseEntity.ok(move);
    }

    /**
     * GET /api/v1/cards/{id}/transactions?limit=10
     * The money moved onto and off the card, newest first (at most 50).
     */
    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CardTransactionResponse>> getCardTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardMoneyService.getCardTransactions(authentication.getName(), id, limit));
    }

    /**
     * POST /api/v1/cards/{id}/details
     * The full card number and CVV, only after the PIN. A POST so the PIN is never in a URL.
     * no-store: the browser and any proxy must not keep a copy of the answer.
     */
    @PostMapping("/{id}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardDetailsResponse> revealDetails(
            @PathVariable UUID id,
            @Valid @RequestBody CardDetailsRequest request,
            Authentication authentication
    ) {
        CardDetailsResponse details = cardDetailsService.revealDetails(authentication.getName(), id, request.pin());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(details);
    }
}
