package com.example.mybank.controller;

import com.example.mybank.dto.requests.CardApplicationRequest;
import com.example.mybank.dto.responses.CardResponse;
import com.example.mybank.services.CardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
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
     * GET /api/v1/cards/me
     * The customer's cards, newest first. Only the last 4 digits, never the full number or CVV.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CardResponse>> getMyCards(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(cardService.getMyCards(userEmail));
    }
}
