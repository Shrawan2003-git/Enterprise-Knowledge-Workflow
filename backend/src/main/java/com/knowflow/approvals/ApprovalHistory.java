package com.knowflow.approvals;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_history")
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "from_state", nullable = false, length = 30)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 30)
    private String toState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ApprovalHistory() {}

    public ApprovalHistory(UUID requestId, UUID actorId, String action, String comment,
                           String fromState, String toState) {
        this.requestId = requestId;
        this.actorId = actorId;
        this.action = action;
        this.comment = comment;
        this.fromState = fromState;
        this.toState = toState;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getComment() { return comment; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public Instant getCreatedAt() { return createdAt; }
}
