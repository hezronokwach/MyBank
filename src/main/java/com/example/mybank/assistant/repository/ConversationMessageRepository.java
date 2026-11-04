package com.example.mybank.assistant.repository;

import com.example.mybank.assistant.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    // The 10 newest messages of a conversation, newest first
    List<ConversationMessage> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
