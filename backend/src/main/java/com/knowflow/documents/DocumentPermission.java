package com.knowflow.documents;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_permissions")
public class DocumentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "principal_type", nullable = false, length = 30)
    private String principalType; // USER, DEPARTMENT, GROUP

    @Column(name = "principal_id", nullable = false)
    private UUID principalId;

    @Column(name = "permission_level", nullable = false, length = 30)
    private String permissionLevel; // VIEW, EDIT, MANAGE

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentPermission() {}

    public DocumentPermission(UUID documentId, String principalType, UUID principalId, String permissionLevel) {
        this.documentId = documentId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.permissionLevel = permissionLevel;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getPrincipalType() { return principalType; }
    public UUID getPrincipalId() { return principalId; }
    public String getPermissionLevel() { return permissionLevel; }
    public Instant getCreatedAt() { return createdAt; }
}
