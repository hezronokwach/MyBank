package com.example.mybank.entity;

import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // =========================================================================
    // FOREIGN KEY RELATIONSHIP TO USER
    // =========================================================================
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 1. No-arg constructor required by JPA
    protected Account() {
    }

    // 2. Parameterized constructor for creation
    public Account(User user, String accountNumber, String currency, AccountType type) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.type = type;
        this.balance = BigDecimal.ZERO; // Starting balance is zero
        this.status = AccountStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Business logic setters / domain updates
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}