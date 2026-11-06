package com.knowflow.copilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "uq_message_feedback_user", columnNames = {"message_id", "user_id"})
})
public class MessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int rating; // 1 (positive / helpful), -1 (negative / unhelpful)

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 50)
    private FeedbackReasonCode reasonCode;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MessageFeedback() {}

    public MessageFeedback(UUID messageId, UUID userId, int rating, FeedbackReasonCode reasonCode, String comment) {
        this.messageId = messageId;
        this.userId = userId;
        this.rating = rating;
        this.reasonCode = reasonCode;
        this.comment = comment;
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

    public FeedbackReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
