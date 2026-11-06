package com.knowflow.tools;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_audit_logs")
public class ToolAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "args_summary", columnDefinition = "TEXT")
    private String argsSummary;

    @Column(nullable = false, length = 50)
    private String outcome; // SUCCESS, FORBIDDEN, RATE_LIMITED, FAILED

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ToolAuditLog() {}

    public ToolAuditLog(UUID actorId, String toolName, String argsSummary, String outcome, String correlationId) {
        this.actorId = actorId;
        this.toolName = toolName;
        this.argsSummary = argsSummary;
        this.outcome = outcome;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgsSummary() {
        return argsSummary;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
