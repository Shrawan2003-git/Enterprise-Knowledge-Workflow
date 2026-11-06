package com.knowflow.tools;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ToolAuditLogRepository extends JpaRepository<ToolAuditLog, UUID> {

    List<ToolAuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    long countByActorIdAndCreatedAtAfter(UUID actorId, java.time.Instant cutoff);
}
