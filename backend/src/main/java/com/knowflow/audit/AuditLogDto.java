package com.knowflow.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> detail,
        String correlationId,
        Instant createdAt
) {
    public static AuditLogDto fromEntity(AuditLog entity) {
        return new AuditLogDto(
                entity.getId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getDetail(),
                entity.getCorrelationId(),
                entity.getCreatedAt()
        );
    }
}
