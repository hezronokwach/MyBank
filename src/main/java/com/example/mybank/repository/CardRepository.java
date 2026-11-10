package com.example.mybank.repository;

import com.example.mybank.entity.Card;
import com.example.mybank.enums.CardStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    // One of the customer's cards, locked so its balance and PIN attempts can't change under us.
    // Someone else's card gives nothing, the same as a card that doesn't exist.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Card> findAndLockByIdAndUserId(UUID id, UUID userId);

    Optional<Card> findByIdAndUserId(UUID id, UUID userId);

    // A customer's cards, newest first
    List<Card> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // For the admin's Card applications page: oldest first, so the longest-waiting come first
    List<Card> findByStatusOrderByCreatedAtAsc(CardStatus status);

    List<Card> findAllByOrderByCreatedAtDesc();

    // Does the customer already have a card with one of these statuses?
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CardStatus> statuses);
}
