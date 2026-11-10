package com.example.mybank.controller;

import com.example.mybank.dto.requests.RejectCardRequest;
import com.example.mybank.dto.responses.AdminCardResponse;
import com.example.mybank.enums.CardStatus;
import com.example.mybank.services.CardApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/cards")
public class AdminCardController {

    private final CardApprovalService cardApprovalService;

    public AdminCardController(CardApprovalService cardApprovalService) {
        this.cardApprovalService = cardApprovalService;
    }

    /**
     * GET /api/v1/admin/cards?status=PENDING_APPROVAL
     * Card applications. Leave out status to list every card.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminCardResponse>> listCards(@RequestParam(required = false) CardStatus status) {
        return ResponseEntity.ok(cardApprovalService.listCards(status));
    }

    /**
     * POST /api/v1/admin/cards/{id}/approve
     * Issues the card: it gets its number, CVV and expiry, and becomes ACTIVE.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCardResponse> approve(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(cardApprovalService.approve(authentication.getName(), id));
    }

    /**
     * POST /api/v1/admin/cards/{id}/reject
     * Refuses the card and gives the fee back to the customer's account.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCardResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectCardRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardApprovalService.reject(authentication.getName(), id, request.reason()));
    }
}
