package com.knowflow.approvals;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/documents/{id}/submit-review")
    public ResponseEntity<ApprovalRequestDto> submitReview(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(approvalService.submitForReview(id, request));
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<ApprovalRequestDto> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) DecisionRequest request
    ) {
        String comment = request != null ? request.comment() : null;
        return ResponseEntity.ok(approvalService.approve(id, comment));
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<ApprovalRequestDto> reject(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request
    ) {
        if (request == null || request.comment() == null) {
            throw new IllegalArgumentException("Comment is required for rejection");
        }
        return ResponseEntity.ok(approvalService.reject(id, request.comment()));
    }

    @PostMapping("/approvals/{id}/request-changes")
    public ResponseEntity<ApprovalRequestDto> requestChanges(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request
    ) {
        if (request == null || request.comment() == null) {
            throw new IllegalArgumentException("Comment is required to request revisions");
        }
        return ResponseEntity.ok(approvalService.requestChanges(id, request.comment()));
    }

    @GetMapping("/approvals/assigned")
    public ResponseEntity<Page<ApprovalRequestDto>> getAssignedApprovals(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(approvalService.listMyAppovals(pageable));
    }

    @GetMapping("/approvals/submitted")
    public ResponseEntity<Page<ApprovalRequestDto>> getSubmittedApprovals(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(approvalService.listMySubmissions(pageable));
    }
}
