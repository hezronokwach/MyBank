package com.example.mybank.repository;

import com.example.mybank.entity.Account;
import com.example.mybank.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Find all accounts belonging to a user
    List<Account> findByUserId(UUID userId);

    Optional<Account> findByUser(User user);

    // Find an account by account number (Admin/System lookup)
    Optional<Account> findByAccountNumber(String accountNumber);

    // Find a specific account owned by a user (Security-enforced lookup)
    Optional<Account> findByAccountNumberAndUserId(String accountNumber, UUID userId);
}