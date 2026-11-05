package com.example.mybank.assistant.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Something the assistant has PREPARED but not done, such as freezing an account.
 * It only happens if the customer presses Confirm in the app within 5 minutes.
 *
 * The version column gives optimistic locking: if two confirms arrive at the same time,
 * only the first one can save, so the action can't run twice.
 */
@Entity
@Table(name = "assistant_pending_actions", indexes = @Index(name = "idx_pending_status_expiry", columnList = "status, expires_at"))
public class PendingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Only this user can confirm or cancel it
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingActionType type;

    // The full account number the action is for (never shown to the model)
    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(length = 200)
    private String reason;

    // What the confirm card shows, such as "Freeze account ...4821"
    @Column(nullable = false, length = 200)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingActionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // When it was confirmed, cancelled or expired
    @Column(name = "decided_at")
    private Instant decidedAt;

    // Success message, or the error if it failed
    @Column(length = 200)
    private String result;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA
    protected PendingAction() {
    }

    public PendingAction(UUID userId, UUID conversationId, PendingActionType type, String accountNumber,
                         String reason, String summary, Instant expiresAt) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.type = type;
        this.accountNumber = accountNumber;
        this.reason = reason;
        this.summary = summary;
        this.status = PendingActionStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public PendingActionType getType() {
        return type;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getReason() {
        return reason;
    }

    public String getSummary() {
        return summary;
    }

    public PendingActionStatus getStatus() {
        return status;
    }

    public void setStatus(PendingActionStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
