package com.example.mybank.assistant.repository;

import com.example.mybank.assistant.entity.AssistantConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {

    // Security-enforced lookup: only finds the conversation if it belongs to this user
    Optional<AssistantConversation> findByIdAndUserId(UUID id, UUID userId);
}
