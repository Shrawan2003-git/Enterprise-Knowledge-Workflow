package com.knowflow.documents;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = "PENDING";

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "active_pipeline_version", length = 50)
    private String activePipelineVersion = "v1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentVersion() {}

    public DocumentVersion(UUID documentId, int versionNumber, String storageKey, String checksum,
                           String mimeType, long sizeBytes, UUID uploadedBy, String changeNote) {
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.checksum = checksum;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.changeNote = changeNote;
        this.processingStatus = "PENDING";
        this.activePipelineVersion = "v1";
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getVersionNumber() { return versionNumber; }
    public String getStorageKey() { return storageKey; }
    public String getChecksum() { return checksum; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public UUID getUploadedBy() { return uploadedBy; }
    public String getChangeNote() { return changeNote; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getActivePipelineVersion() { return activePipelineVersion; }
    public void setActivePipelineVersion(String activePipelineVersion) { this.activePipelineVersion = activePipelineVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
