package com.knowflow.copilot;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "message_citations")
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "source_id", length = 20)
    private String sourceId;

    protected MessageCitation() {}

    public MessageCitation(UUID messageId, UUID chunkId, UUID documentId, UUID versionId, int versionNumber,
                           Integer pageStart, Integer pageEnd, String snippet, String sourceId) {
        this.messageId = messageId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.versionId = versionId;
        this.versionNumber = versionNumber;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.snippet = snippet;
        this.sourceId = sourceId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getSourceId() {
        return sourceId;
    }
}
