package com.knowflow.approvals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    Page<ApprovalRequest> findByApproverId(UUID approverId, Pageable pageable);
    Page<ApprovalRequest> findBySubmittedBy(UUID submittedBy, Pageable pageable);
    Optional<ApprovalRequest> findByDocumentVersionId(UUID documentVersionId);
}
