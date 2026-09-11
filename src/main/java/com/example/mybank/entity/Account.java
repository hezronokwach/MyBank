package com.example.mybank.entity;

import com.example.mybank.enums.AccountStatus;
import com.example.mybank.enums.AccountTier;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountTier tier;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "pin", nullable = false)
    private String pin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 1. No-arg constructor required by JPA
    protected Account() {
    }

    // 2. Parameterized constructor for creation
    // Constructor for provisioning a new account
    public Account(
            String accountNumber,
            BigDecimal balance,
            AccountStatus status,
            AccountTier tier,
            AccountType type,
            String currency,
            User user,
            String pin
    ) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
        this.tier = tier;
        this.type = type;
        this.currency = currency;
        this.user = user;
        this.pin = pin;
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

    public void setUser(User user) {
        this.user = user;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public AccountTier getTier() {
        return tier;
    }

    public void setTier(AccountTier tier) {
        this.tier = tier;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
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