package com.example.mybank.entity;

import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import com.example.mybank.enums.TransactionStatus;
import com.example.mybank.enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    // Source account (can be null for DEPOSIT operations)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    // Destination account (can be null for WITHDRAWAL operations)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Required by JPA/Hibernate.
     */
    protected Transaction() {
    }

    /**
     * Parameterized constructor for transfers/deposits/withdrawals.
     */
    public Transaction(String reference, Account fromAccount, Account toAccount,
                       BigDecimal amount, TransactionType type, TransactionStatus status) {
        this.reference = reference;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.type = type;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // =========================================================================
    // GETTERS (No Setters to maintain immutability of ledger records)
    // =========================================================================

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
