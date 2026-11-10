package com.example.mybank.entity;

import com.example.mybank.enums.CardStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A virtual card. It has its own balance, like a wallet: the customer moves money between
 * the linked account and the card.
 *
 * The card number, CVV and expiry are only created when an admin approves the card,
 * and the number and CVV are stored encrypted (never in plain text).
 */
@Entity
@Table(name = "cards", indexes = @Index(name = "idx_cards_user", columnList = "user_id"))
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The account that paid the fee. The card uses this account's PIN.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "fee_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal feePaid;

    // Filled in when an admin approves the card
    @Column(length = 4)
    private String last4;

    @Column(name = "card_number_encrypted")
    private String cardNumberEncrypted;

    @Column(name = "cvv_encrypted")
    private String cvvEncrypted;

    @Column(name = "expiry_month")
    private Integer expiryMonth;

    @Column(name = "expiry_year")
    private Integer expiryYear;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    // Email of the admin who approved or rejected it
    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "rejection_reason", length = 200)
    private String rejectionReason;

    // Two admins acting on the same card at the same moment: only the first save succeeds
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA
    protected Card() {
    }

    // A new application: no money on it yet, waiting for an admin
    public Card(User user, Account account, String currency, BigDecimal feePaid) {
        this.user = user;
        this.account = account;
        this.currency = currency;
        this.feePaid = feePaid;
        this.balance = BigDecimal.ZERO;
        this.status = CardStatus.PENDING_APPROVAL;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Account getAccount() {
        return account;
    }

    public CardStatus getStatus() {
        return status;
    }

    public void setStatus(CardStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getFeePaid() {
        return feePaid;
    }

    public String getLast4() {
        return last4;
    }

    public void setLast4(String last4) {
        this.last4 = last4;
    }

    public String getCardNumberEncrypted() {
        return cardNumberEncrypted;
    }

    public void setCardNumberEncrypted(String cardNumberEncrypted) {
        this.cardNumberEncrypted = cardNumberEncrypted;
    }

    public String getCvvEncrypted() {
        return cvvEncrypted;
    }

    public void setCvvEncrypted(String cvvEncrypted) {
        this.cvvEncrypted = cvvEncrypted;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(Integer expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(Integer expiryYear) {
        this.expiryYear = expiryYear;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
