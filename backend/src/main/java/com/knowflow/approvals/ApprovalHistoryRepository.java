package com.knowflow.approvals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for approval history. NO update or delete operations allowed.
 */
@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, UUID> {
    List<ApprovalHistory> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
