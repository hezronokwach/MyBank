package com.example.mybank.assistant.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit row for every tool the model calls: who, which tool, with what arguments, and what happened.
 * Ledger-style: no setters except linking the row to its answer.
 */
@Entity
@Table(name = "assistant_tool_calls", indexes = @Index(name = "idx_tool_calls_user", columnList = "user_id, created_at"))
public class AssistantToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Null until the answer is saved: tools run before the assistant message exists
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tool_name", nullable = false, length = 60)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ToolOutcome outcome;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantToolCall() {
    }

    public AssistantToolCall(UUID userId, String toolName, String arguments, ToolOutcome outcome, long durationMs) {
        this.userId = userId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.outcome = outcome;
        this.durationMs = durationMs;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public ToolOutcome getOutcome() {
        return outcome;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
