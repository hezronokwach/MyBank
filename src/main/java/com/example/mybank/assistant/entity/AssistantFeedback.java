package com.example.mybank.assistant.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer's thumbs up (1) or thumbs down (-1) on one assistant answer, with an optional comment.
 * One row per answer per customer: rating the same answer again replaces the old rating.
 */
@Entity
@Table(name = "assistant_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_feedback_message_user", columnNames = {"message_id", "user_id"}))
public class AssistantFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The assistant answer being rated
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // 1 = thumbs up, -1 = thumbs down
    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Required by JPA
    protected AssistantFeedback() {
    }

    public AssistantFeedback(UUID messageId, UUID userId, int rating, String comment) {
        this.messageId = messageId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
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

    public UUID getUserId() {
        return userId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
