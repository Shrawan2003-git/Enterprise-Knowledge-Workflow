package com.knowflow.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log repository. Intentionally provides NO update or delete operations.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:entityId IS NULL OR a.entityId = :entityId) AND " +
           "(:fromTime IS NULL OR a.createdAt >= :fromTime) AND " +
           "(:toTime IS NULL OR a.createdAt <= :toTime)")
    Page<AuditLog> findWithFilters(
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );
}
