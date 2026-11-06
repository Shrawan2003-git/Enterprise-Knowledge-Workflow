package com.knowflow.approvals;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private int version;

    public ApprovalRequest() {}

    public ApprovalRequest(UUID documentVersionId, UUID submittedBy, UUID approverId) {
        this.documentVersionId = documentVersionId;
        this.submittedBy = submittedBy;
        this.approverId = approverId;
        this.status = "PENDING";
    }

    public UUID getId() { return id; }
    public UUID getDocumentVersionId() { return documentVersionId; }
    public UUID getSubmittedBy() { return submittedBy; }
    public UUID getApproverId() { return approverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getVersion() { return version; }
}
