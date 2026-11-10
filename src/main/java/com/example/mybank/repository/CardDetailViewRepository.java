package com.example.mybank.repository;

import com.example.mybank.entity.CardDetailView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardDetailViewRepository extends JpaRepository<CardDetailView, UUID> {

    // Every attempt on one card, newest first
    List<CardDetailView> findByCardIdOrderByCreatedAtDesc(UUID cardId);
}
