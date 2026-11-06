package com.knowflow.documents;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "department_id")
    private UUID departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 30)
    private String classification = "INTERNAL";

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private DocumentVersion currentVersion;

    @Column(name = "expiry_at")
    private Instant expiryAt;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_tags",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Document() {}

    public Document(String title, String description, UUID ownerId, UUID departmentId,
                    Category category, String classification) {
        this.title = title;
        this.description = description;
        this.ownerId = ownerId;
        this.departmentId = departmentId;
        this.category = category;
        this.classification = classification;
        this.status = "DRAFT";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public DocumentVersion getCurrentVersion() { return currentVersion; }
    public UUID getCurrentVersionId() { return currentVersion != null ? currentVersion.getId() : null; }
    public void setCurrentVersion(DocumentVersion currentVersion) { this.currentVersion = currentVersion; }
    public Instant getExpiryAt() { return expiryAt; }
    public void setExpiryAt(Instant expiryAt) { this.expiryAt = expiryAt; }
    public int getVersion() { return version; }
    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isExpired(Instant now) {
        return expiryAt != null && now.isAfter(expiryAt);
    }
}
