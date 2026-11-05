package com.example.mybank.assistant.repository;

import com.example.mybank.assistant.entity.PendingAction;
import com.example.mybank.assistant.entity.PendingActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    // Security-enforced lookup: only finds the action if it belongs to this user
    Optional<PendingAction> findByIdAndUserId(UUID id, UUID userId);

    // For the expiry job: actions still waiting after their time ran out
    List<PendingAction> findByStatusAndExpiresAtBefore(PendingActionStatus status, Instant time);
}
