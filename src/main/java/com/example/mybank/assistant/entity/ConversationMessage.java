package com.example.mybank.assistant.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in a conversation: either the customer's question (USER) or the assistant's answer (ASSISTANT).
 * Answers also record which prompt and model produced them, the tokens used, how long it took and the outcome.
 *
 * (Named ConversationMessage, not AssistantMessage, because Spring AI already has a class called AssistantMessage.)
 */
@Entity
@Table(name = "assistant_messages", indexes = @Index(name = "idx_messages_conversation", columnList = "conversation_id, created_at"))
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // The fields below are only filled in for ASSISTANT messages

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(length = 60)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MessageOutcome outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA
    protected ConversationMessage() {
    }

    // For the customer's question
    public ConversationMessage(UUID conversationId, MessageRole role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
    }

    // For the assistant's answer
    public ConversationMessage(UUID conversationId, String content, String promptVersion, String model,
                               Integer inputTokens, Integer outputTokens, long latencyMs, MessageOutcome outcome) {
        this.conversationId = conversationId;
        this.role = MessageRole.ASSISTANT;
        this.content = content;
        this.promptVersion = promptVersion;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.outcome = outcome;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public MessageOutcome getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
