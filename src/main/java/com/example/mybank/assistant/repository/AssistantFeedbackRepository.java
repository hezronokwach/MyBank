package com.example.mybank.assistant.repository;

import com.example.mybank.assistant.entity.AssistantFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistantFeedbackRepository extends JpaRepository<AssistantFeedback, UUID> {

    // The customer's earlier rating of this answer, if they already gave one
    Optional<AssistantFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
