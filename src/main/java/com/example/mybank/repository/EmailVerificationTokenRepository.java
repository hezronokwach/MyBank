package com.example.mybank.repository;

import com.example.mybank.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    // How many links were sent to this user since a moment, for the "3 an hour" limit
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
