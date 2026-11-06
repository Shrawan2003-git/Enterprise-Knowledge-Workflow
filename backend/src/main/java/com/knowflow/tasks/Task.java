package com.knowflow.tasks;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "assignee_id", nullable = false)
    private UUID assigneeId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "linked_document_id")
    private UUID linkedDocumentId;

    @Column(name = "linked_approval_id")
    private UUID linkedApprovalId;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Task() {}

    public Task(String title, String description, UUID assigneeId, UUID creatorId,
                UUID linkedDocumentId, UUID linkedApprovalId, String priority, Instant dueAt) {
        this.title = title;
        this.description = description;
        this.assigneeId = assigneeId;
        this.creatorId = creatorId;
        this.linkedDocumentId = linkedDocumentId;
        this.linkedApprovalId = linkedApprovalId;
        this.priority = priority != null ? priority.toUpperCase() : "MEDIUM";
        this.dueAt = dueAt;
        this.status = "OPEN";
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getAssigneeId() { return assigneeId; }
    public UUID getCreatorId() { return creatorId; }
    public UUID getLinkedDocumentId() { return linkedDocumentId; }
    public UUID getLinkedApprovalId() { return linkedApprovalId; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public String getCompletionNote() { return completionNote; }
    public void setCompletionNote(String completionNote) { this.completionNote = completionNote; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
