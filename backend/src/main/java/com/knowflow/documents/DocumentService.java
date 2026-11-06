package com.knowflow.documents;

import com.knowflow.audit.AuditService;
import com.knowflow.ingestion.IngestionJob;
import com.knowflow.ingestion.IngestionJobRepository;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final FileStorage fileStorage;
    private final FileValidationService fileValidationService;
    private final DocumentAccessPolicy accessPolicy;
    private final AuditService auditService;
    private final DataSize maxFileSize;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentPermissionRepository permissionRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            IngestionJobRepository ingestionJobRepository,
            FileStorage fileStorage,
            FileValidationService fileValidationService,
            DocumentAccessPolicy accessPolicy,
            AuditService auditService,
            @Value("${spring.servlet.multipart.max-file-size:50MB}") DataSize maxFileSize
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.permissionRepository = permissionRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.fileStorage = fileStorage;
        this.fileValidationService = fileValidationService;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.maxFileSize = maxFileSize;
    }

    public DocumentDetailDto uploadDocument(MultipartFile file, CreateDocumentMetadata metadata) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();

        // 1. File size & basic format checks
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new IllegalArgumentException("File size exceeds allowed maximum: " + file.getSize() + " > " + maxFileSize.toBytes());
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload payload", e);
        }

        fileValidationService.validatePdfMagicBytes(bytes);
        String checksum = fileValidationService.calculateSha256(bytes);

        // 2. Physical storage with cleanup rollback guarantee
        String storageKey = fileStorage.store(new ByteArrayInputStream(bytes), "pdf");

        try {
            Category category = null;
            if (metadata.categoryId() != null) {
                category = categoryRepository.findById(metadata.categoryId()).orElse(null);
            }

            Document document = new Document(
                    metadata.title(),
                    metadata.description(),
                    user.id(),
                    metadata.departmentId() != null ? metadata.departmentId() : user.departmentId(),
                    category,
                    metadata.classification() != null ? metadata.classification().toUpperCase() : "INTERNAL"
            );
            document.setExpiryAt(metadata.expiryAt());

            if (metadata.tags() != null && !metadata.tags().isEmpty()) {
                Set<Tag> tagEntities = metadata.tags().stream()
                        .map(t -> tagRepository.findByNameIgnoreCase(t.trim())
                                .orElseGet(() -> tagRepository.save(new Tag(t.trim()))))
                        .collect(Collectors.toSet());
                document.setTags(tagEntities);
            }

            Document savedDoc = documentRepository.save(document);

            // Version 1
            DocumentVersion version1 = new DocumentVersion(
                    savedDoc.getId(),
                    1,
                    storageKey,
                    checksum,
                    "application/pdf",
                    file.getSize(),
                    user.id(),
                    metadata.changeNote() != null ? metadata.changeNote() : "Initial upload"
            );
            DocumentVersion savedVersion = versionRepository.save(version1);

            savedDoc.setCurrentVersion(savedVersion);
            documentRepository.save(savedDoc);

            // Placeholder ingestion job
            IngestionJob job = new IngestionJob(savedVersion.getId(), "v1");
            ingestionJobRepository.save(job);

            auditService.record(user.id(), "DOCUMENT_UPLOAD", "DOCUMENT", savedDoc.getId(),
                    Map.of("title", savedDoc.getTitle(), "version", 1, "checksum", checksum));

            return DocumentDetailDto.fromEntity(savedDoc, List.of());
        } catch (Exception e) {
            // Delete stored file if DB commit fails
            fileStorage.delete(storageKey);
            throw e;
        }
    }

    public VersionDto uploadNewVersion(UUID documentId, MultipartFile file, String changeNote) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult editPerm = accessPolicy.canEdit(user, documentId);
        if (!editPerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(editPerm.message());
        }

        if (file.isEmpty()) throw new IllegalArgumentException("File cannot be empty");
        if (file.getSize() > maxFileSize.toBytes()) throw new IllegalArgumentException("File size exceeds limit");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read payload", e);
        }

        fileValidationService.validatePdfMagicBytes(bytes);
        String checksum = fileValidationService.calculateSha256(bytes);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Determine next incremental version number
        int nextVersionNum = versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        String storageKey = fileStorage.store(new ByteArrayInputStream(bytes), "pdf");

        try {
            DocumentVersion newVersion = new DocumentVersion(
                    document.getId(),
                    nextVersionNum,
                    storageKey,
                    checksum,
                    "application/pdf",
                    file.getSize(),
                    user.id(),
                    changeNote != null ? changeNote : "Version " + nextVersionNum
            );
            DocumentVersion savedVersion = versionRepository.save(newVersion);

            // New version becomes draft candidate; does NOT overwrite in-progress reviews
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);

            IngestionJob job = new IngestionJob(savedVersion.getId(), "v1");
            ingestionJobRepository.save(job);

            auditService.record(user.id(), "NEW_VERSION_UPLOAD", "DOCUMENT_VERSION", savedVersion.getId(),
                    Map.of("documentId", documentId.toString(), "versionNumber", nextVersionNum));

            return VersionDto.fromEntity(savedVersion);
        } catch (Exception e) {
            fileStorage.delete(storageKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public DocumentDetailDto getDocumentById(UUID id) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult result = accessPolicy.canView(user, id);
        if (!result.allowed()) {
            // Return 404 existence hiding
            throw new NoSuchElementException("Document not found");
        }

        Document document = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        List<DocumentPermissionDto> perms = permissionRepository.findByDocumentId(id).stream()
                .map(DocumentPermissionDto::fromEntity)
                .toList();

        return DocumentDetailDto.fromEntity(document, perms);
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummaryDto> listDocuments(Pageable pageable) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        Page<Document> page = documentRepository.findAllActive(pageable);

        // Filter through DocumentAccessPolicy
        List<DocumentSummaryDto> allowed = page.getContent().stream()
                .filter(doc -> accessPolicy.canView(user, doc.getId()).allowed())
                .map(DocumentSummaryDto::fromEntity)
                .toList();

        return new PageImpl<>(allowed, pageable, allowed.size());
    }

    @Transactional(readOnly = true)
    public List<VersionDto> listVersions(UUID documentId) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        if (!accessPolicy.canView(user, documentId).allowed()) {
            throw new NoSuchElementException("Document not found");
        }

        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(VersionDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(UUID documentId, Integer versionNumber) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult downloadPerm = accessPolicy.canDownload(user, documentId, versionNumber);
        if (!downloadPerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(downloadPerm.message());
        }

        DocumentVersion version;
        if (versionNumber != null) {
            version = versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
                    .orElseThrow(() -> new NoSuchElementException("Version not found"));
        } else {
            Document doc = documentRepository.findById(documentId)
                    .orElseThrow(() -> new NoSuchElementException("Document not found"));
            version = doc.getCurrentVersion();
            if (version == null) {
                version = versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                        .orElseThrow(() -> new NoSuchElementException("No version found for document"));
            }
        }

        InputStream stream = fileStorage.retrieve(version.getStorageKey());

        // Audit download for sensitive classifications
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc != null && ("CONFIDENTIAL".equalsIgnoreCase(doc.getClassification()) || "RESTRICTED".equalsIgnoreCase(doc.getClassification()))) {
            auditService.record(user.id(), "DOWNLOAD_CONFIDENTIAL_DOCUMENT", "DOCUMENT", documentId,
                    Map.of("versionNumber", version.getVersionNumber(), "classification", doc.getClassification()));
        }

        return new InputStreamResource(stream);
    }

    public DocumentDetailDto updateMetadata(UUID documentId, UpdateDocumentRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult editPerm = accessPolicy.canEdit(user, documentId);
        if (!editPerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(editPerm.message());
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        // Optimistic locking check
        if (document.getVersion() != request.version()) {
            throw new IllegalStateException("Document has been modified concurrently. Expected version: " + document.getVersion());
        }

        document.setTitle(request.title());
        document.setDescription(request.description());
        if (request.classification() != null) {
            document.setClassification(request.classification().toUpperCase());
        }
        document.setExpiryAt(request.expiryAt());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId()).orElse(null);
            document.setCategory(category);
        }

        if (request.tags() != null) {
            Set<Tag> tagEntities = request.tags().stream()
                    .map(t -> tagRepository.findByNameIgnoreCase(t.trim())
                            .orElseGet(() -> tagRepository.save(new Tag(t.trim()))))
                    .collect(Collectors.toSet());
            document.setTags(tagEntities);
        }

        document.setUpdatedAt(Instant.now());
        Document saved = documentRepository.save(document);

        auditService.record(user.id(), "UPDATE_DOCUMENT_METADATA", "DOCUMENT", documentId,
                Map.of("newTitle", saved.getTitle(), "version", saved.getVersion()));

        List<DocumentPermissionDto> perms = permissionRepository.findByDocumentId(documentId).stream()
                .map(DocumentPermissionDto::fromEntity)
                .toList();

        return DocumentDetailDto.fromEntity(saved, perms);
    }

    public void updatePermissions(UUID documentId, List<DocumentPermissionDto> newPermissions) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult managePerm = accessPolicy.canManagePermissions(user, documentId);
        if (!managePerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(managePerm.message());
        }

        permissionRepository.deleteByDocumentId(documentId);

        if (newPermissions != null) {
            for (DocumentPermissionDto dto : newPermissions) {
                DocumentPermission perm = new DocumentPermission(
                        documentId,
                        dto.principalType().toUpperCase(),
                        dto.principalId(),
                        dto.permissionLevel().toUpperCase()
                );
                permissionRepository.save(perm);
            }
        }

        auditService.record(user.id(), "UPDATE_PERMISSIONS", "DOCUMENT", documentId,
                Map.of("permissionCount", newPermissions != null ? newPermissions.size() : 0));
    }

    public void archiveDocument(UUID documentId) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult managePerm = accessPolicy.canManagePermissions(user, documentId);
        if (!managePerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(managePerm.message());
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        document.setStatus("ARCHIVED");
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        auditService.record(user.id(), "ARCHIVE_DOCUMENT", "DOCUMENT", documentId, Map.of());
    }

    public void restoreDocument(UUID documentId) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        PolicyResult managePerm = accessPolicy.canManagePermissions(user, documentId);
        if (!managePerm.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(managePerm.message());
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        document.setStatus("DRAFT");
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        auditService.record(user.id(), "RESTORE_DOCUMENT", "DOCUMENT", documentId, Map.of());
    }
}
