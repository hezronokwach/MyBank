package com.example.mybank.repository;

import com.example.mybank.entity.Card;
import com.example.mybank.enums.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    // A customer's cards, newest first
    List<Card> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Does the customer already have a card with one of these statuses?
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CardStatus> statuses);
}
