package com.knowflow.ingestion;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "pipeline_version", nullable = false, length = 50)
    private String pipelineVersion = "v1";

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "page_start", nullable = false)
    private int pageStart;

    @Column(name = "page_end", nullable = false)
    private int pageEnd;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentChunk() {}

    public DocumentChunk(UUID documentId, UUID versionId, String pipelineVersion, int chunkIndex,
                         String text, int pageStart, int pageEnd, int tokenCount, String contentHash) {
        this.documentId = documentId;
        this.versionId = versionId;
        this.pipelineVersion = pipelineVersion != null ? pipelineVersion : "v1";
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.tokenCount = tokenCount;
        this.contentHash = contentHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }
    public String getPipelineVersion() { return pipelineVersion; }
    public int getChunkIndex() { return chunkIndex; }
    public String getText() { return text; }
    public int getPageStart() { return pageStart; }
    public int getPageEnd() { return pageEnd; }
    public int getTokenCount() { return tokenCount; }
    public String getContentHash() { return contentHash; }
    public Instant getCreatedAt() { return createdAt; }
}
