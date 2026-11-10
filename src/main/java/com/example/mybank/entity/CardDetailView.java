package com.example.mybank.entity;

import com.example.mybank.enums.CardRevealOutcome;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to see a card's full number and CVV: who, which card, when, and how it ended.
 * Never the PIN, the card number or the CVV.
 */
@Entity
@Table(name = "card_detail_views", indexes = @Index(name = "idx_card_detail_views_card", columnList = "card_id, created_at"))
public class CardDetailView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardRevealOutcome outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA
    protected CardDetailView() {
    }

    public CardDetailView(UUID cardId, UUID userId, CardRevealOutcome outcome) {
        this.cardId = cardId;
        this.userId = userId;
        this.outcome = outcome;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getUserId() {
        return userId;
    }

    public CardRevealOutcome getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
