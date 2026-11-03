package com.example.mybank.assistant.repository;

import com.example.mybank.assistant.entity.AssistantToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistantToolCallRepository extends JpaRepository<AssistantToolCall, UUID> {
    List<AssistantToolCall> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
