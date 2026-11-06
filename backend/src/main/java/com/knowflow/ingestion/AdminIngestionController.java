package com.knowflow.ingestion;

import com.knowflow.audit.AuditService;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ingestion")
public class AdminIngestionController {

    private final IngestionJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final JobDispatcher jobDispatcher;
    private final AuditService auditService;

    public AdminIngestionController(
            IngestionJobRepository jobRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            JobDispatcher jobDispatcher,
            AuditService auditService
    ) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.jobDispatcher = jobDispatcher;
        this.auditService = auditService;
    }

    @GetMapping("/jobs")
    public ResponseEntity<Page<AdminJobDto>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID versionId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        if (!user.isAdmin() && !user.roles().contains("KNOWLEDGE_MANAGER")) {
            throw new org.springframework.security.access.AccessDeniedException("Requires ADMIN or KNOWLEDGE_MANAGER role");
        }

        Page<IngestionJob> page;
        if (status != null && !status.isBlank()) {
            page = jobRepository.findByStatus(status.toUpperCase(), pageable);
        } else {
            page = jobRepository.findAll(pageable);
        }

        return ResponseEntity.ok(page.map(AdminJobDto::fromEntity));
    }

    @PostMapping("/jobs/{id}/retry")
    @Transactional
    public ResponseEntity<AdminJobDto> retryJob(
            @PathVariable UUID id,
            @Valid @RequestBody RetryJobRequest request
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        if (!user.isAdmin() && !user.roles().contains("KNOWLEDGE_MANAGER")) {
            throw new org.springframework.security.access.AccessDeniedException("Requires ADMIN or KNOWLEDGE_MANAGER role");
        }

        IngestionJob job = jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Ingestion job not found: " + id));

        job.setStatus(IngestionStatus.PENDING.name());
        job.setAttempts(0);
        job.setNextAttemptAt(Instant.now());
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setUpdatedAt(Instant.now());

        IngestionJob saved = jobRepository.save(job);

        auditService.record(user.id(), "INGESTION_JOB_RETRY", "INGESTION_JOB", job.getId(),
                Map.of("reason", request.reason(), "versionId", job.getVersionId()));

        return ResponseEntity.ok(AdminJobDto.fromEntity(saved));
    }

    @PostMapping("/documents/{documentId}/reprocess")
    @Transactional
    public ResponseEntity<AdminJobDto> reprocessDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody ReprocessRequest request
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        if (!user.isAdmin() && !user.roles().contains("KNOWLEDGE_MANAGER")) {
            throw new org.springframework.security.access.AccessDeniedException("Requires ADMIN or KNOWLEDGE_MANAGER role");
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (doc.getCurrentVersionId() == null) {
            throw new IllegalStateException("Document has no current version to reprocess");
        }

        // Idempotently enqueue for the new pipeline version
        IngestionJob job = jobDispatcher.enqueue(doc.getCurrentVersionId(), request.pipelineVersion());

        auditService.record(user.id(), "DOCUMENT_REPROCESS", "DOCUMENT", doc.getId(),
                Map.of("reason", request.reason(), "pipelineVersion", request.pipelineVersion(), "jobId", job.getId()));

        return ResponseEntity.ok(AdminJobDto.fromEntity(job));
    }
}
