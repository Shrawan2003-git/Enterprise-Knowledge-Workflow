package com.knowflow.approvals;

import com.knowflow.audit.AuditService;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.identity.UserRepository;
import com.knowflow.notifications.NotificationSender;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;
    private final AuditService auditService;

    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            ApprovalHistoryRepository historyRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            UserRepository userRepository,
            NotificationSender notificationSender,
            AuditService auditService
    ) {
        this.approvalRepository = approvalRepository;
        this.historyRepository = historyRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
        this.auditService = auditService;
    }

    public ApprovalRequestDto submitForReview(UUID documentId, SubmitReviewRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();

        // 1. Rule: Self-approval is strictly forbidden
        if (user.id().equals(request.approverId())) {
            throw new IllegalArgumentException("Submitter cannot assign themselves as approver");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        DocumentVersion version = versionRepository.findById(request.versionId())
                .orElseThrow(() -> new NoSuchElementException("Version not found"));

        if (!version.getDocumentId().equals(documentId)) {
            throw new IllegalArgumentException("Version does not belong to the specified document");
        }

        // Verify approver exists and has APPROVER role
        userRepository.findById(request.approverId())
                .filter(u -> u.getRoles().stream().anyMatch(r -> "APPROVER".equalsIgnoreCase(r.getName()) || "ADMIN".equalsIgnoreCase(r.getName())))
                .orElseThrow(() -> new IllegalArgumentException("Assigned user does not hold the APPROVER role"));

        // Validate transition
        ApprovalStateMachine.validateTransition(ApprovalStateMachine.State.DRAFT, ApprovalStateMachine.State.PENDING);

        // Check if there is already an active pending approval for this version
        approvalRepository.findByDocumentVersionId(version.getId()).ifPresent(existing -> {
            if ("PENDING".equalsIgnoreCase(existing.getStatus())) {
                throw new IllegalStateException("An approval request is already pending for this version");
            }
        });

        ApprovalRequest approval = new ApprovalRequest(version.getId(), user.id(), request.approverId());
        ApprovalRequest saved = approvalRepository.save(approval);

        // History entry
        ApprovalHistory history = new ApprovalHistory(
                saved.getId(), user.id(), "SUBMIT", "Submitted for review", "DRAFT", "PENDING"
        );
        historyRepository.save(history);

        // Update document status to IN_REVIEW
        document.setStatus("IN_REVIEW");
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        // Notification to Approver
        String dedupeKey = "APPROVAL_ASSIGNED_" + saved.getId();
        notificationSender.sendNotification(
                request.approverId(),
                "APPROVAL_ASSIGNED",
                "New Document Review Assigned",
                "You have been assigned to review: " + document.getTitle() + " (v" + version.getVersionNumber() + ")",
                Map.of("approvalId", saved.getId(), "documentId", documentId),
                dedupeKey
        );

        auditService.record(user.id(), "SUBMIT_APPROVAL", "APPROVAL_REQUEST", saved.getId(),
                Map.of("documentId", documentId, "versionNumber", version.getVersionNumber()));

        return ApprovalRequestDto.fromEntity(saved, List.of(ApprovalHistoryDto.fromEntity(history)));
    }

    public ApprovalRequestDto approve(UUID approvalId, String comment) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found"));

        if (!user.isAdmin() && !user.id().equals(approval.getApproverId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the designated approver can act on this review");
        }

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new IllegalStateException("Review request is not in PENDING status (current: " + approval.getStatus() + ")");
        }

        ApprovalStateMachine.validateTransition(ApprovalStateMachine.State.PENDING, ApprovalStateMachine.State.APPROVED);

        approval.setStatus("APPROVED");
        approval.setCompletedAt(Instant.now());
        ApprovalRequest saved = approvalRepository.save(approval);

        ApprovalHistory history = new ApprovalHistory(
                saved.getId(), user.id(), "APPROVE", comment != null ? comment : "Approved", "PENDING", "APPROVED"
        );
        historyRepository.save(history);

        // Update target Document & version pointers
        DocumentVersion version = versionRepository.findById(approval.getDocumentVersionId()).orElseThrow();
        Document document = documentRepository.findById(version.getDocumentId()).orElseThrow();

        document.setStatus("APPROVED");
        document.setCurrentVersion(version);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        // Notification to document submitter & owner
        notificationSender.sendNotification(
                approval.getSubmittedBy(),
                "APPROVAL_DECISION",
                "Document Approved",
                "Version " + version.getVersionNumber() + " of '" + document.getTitle() + "' has been approved.",
                Map.of("documentId", document.getId(), "status", "APPROVED"),
                "APPROVAL_APPROVED_" + saved.getId()
        );

        auditService.record(user.id(), "APPROVE_DOCUMENT", "APPROVAL_REQUEST", saved.getId(),
                Map.of("documentId", document.getId(), "versionNumber", version.getVersionNumber()));

        List<ApprovalHistoryDto> historyList = historyRepository.findByRequestIdOrderByCreatedAtAsc(saved.getId()).stream()
                .map(ApprovalHistoryDto::fromEntity)
                .toList();

        return ApprovalRequestDto.fromEntity(saved, historyList);
    }

    public ApprovalRequestDto reject(UUID approvalId, String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("A justification comment is required when rejecting a document review");
        }

        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found"));

        if (!user.isAdmin() && !user.id().equals(approval.getApproverId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the designated approver can act on this review");
        }

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new IllegalStateException("Review request is not in PENDING status");
        }

        ApprovalStateMachine.validateTransition(ApprovalStateMachine.State.PENDING, ApprovalStateMachine.State.REJECTED);

        approval.setStatus("REJECTED");
        approval.setCompletedAt(Instant.now());
        ApprovalRequest saved = approvalRepository.save(approval);

        ApprovalHistory history = new ApprovalHistory(
                saved.getId(), user.id(), "REJECT", comment, "PENDING", "REJECTED"
        );
        historyRepository.save(history);

        DocumentVersion version = versionRepository.findById(approval.getDocumentVersionId()).orElseThrow();
        Document document = documentRepository.findById(version.getDocumentId()).orElseThrow();

        // Notification
        notificationSender.sendNotification(
                approval.getSubmittedBy(),
                "APPROVAL_DECISION",
                "Document Review Rejected",
                "Version " + version.getVersionNumber() + " of '" + document.getTitle() + "' was rejected: " + comment,
                Map.of("documentId", document.getId(), "status", "REJECTED"),
                "APPROVAL_REJECTED_" + saved.getId()
        );

        auditService.record(user.id(), "REJECT_DOCUMENT", "APPROVAL_REQUEST", saved.getId(),
                Map.of("documentId", document.getId(), "comment", comment));

        List<ApprovalHistoryDto> historyList = historyRepository.findByRequestIdOrderByCreatedAtAsc(saved.getId()).stream()
                .map(ApprovalHistoryDto::fromEntity)
                .toList();

        return ApprovalRequestDto.fromEntity(saved, historyList);
    }

    public ApprovalRequestDto requestChanges(UUID approvalId, String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("A comment detailing required changes is required");
        }

        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        ApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NoSuchElementException("Approval request not found"));

        if (!user.isAdmin() && !user.id().equals(approval.getApproverId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the designated approver can act on this review");
        }

        ApprovalStateMachine.validateTransition(ApprovalStateMachine.State.PENDING, ApprovalStateMachine.State.CHANGES_REQUESTED);

        approval.setStatus("CHANGES_REQUESTED");
        ApprovalRequest saved = approvalRepository.save(approval);

        ApprovalHistory history = new ApprovalHistory(
                saved.getId(), user.id(), "REQUEST_CHANGES", comment, "PENDING", "CHANGES_REQUESTED"
        );
        historyRepository.save(history);

        DocumentVersion version = versionRepository.findById(approval.getDocumentVersionId()).orElseThrow();
        Document document = documentRepository.findById(version.getDocumentId()).orElseThrow();

        notificationSender.sendNotification(
                approval.getSubmittedBy(),
                "APPROVAL_CHANGES_REQUESTED",
                "Revisions Requested for Document",
                "Changes requested for '" + document.getTitle() + "': " + comment,
                Map.of("documentId", document.getId()),
                "APPROVAL_CHANGES_" + saved.getId()
        );

        auditService.record(user.id(), "REQUEST_CHANGES", "APPROVAL_REQUEST", saved.getId(), Map.of("comment", comment));

        List<ApprovalHistoryDto> historyList = historyRepository.findByRequestIdOrderByCreatedAtAsc(saved.getId()).stream()
                .map(ApprovalHistoryDto::fromEntity)
                .toList();

        return ApprovalRequestDto.fromEntity(saved, historyList);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequestDto> listMyAppovals(Pageable pageable) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return approvalRepository.findByApproverId(user.id(), pageable).map(req -> {
            List<ApprovalHistoryDto> h = historyRepository.findByRequestIdOrderByCreatedAtAsc(req.getId()).stream()
                    .map(ApprovalHistoryDto::fromEntity)
                    .toList();
            return ApprovalRequestDto.fromEntity(req, h);
        });
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequestDto> listMySubmissions(Pageable pageable) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return approvalRepository.findBySubmittedBy(user.id(), pageable).map(req -> {
            List<ApprovalHistoryDto> h = historyRepository.findByRequestIdOrderByCreatedAtAsc(req.getId()).stream()
                    .map(ApprovalHistoryDto::fromEntity)
                    .toList();
            return ApprovalRequestDto.fromEntity(req, h);
        });
    }
}
