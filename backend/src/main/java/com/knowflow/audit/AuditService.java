package com.knowflow.audit;

import com.knowflow.common.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AuditService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "password_hash", "token", "refreshToken", "accessToken",
            "secret", "authorization", "apiKey"
    );

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an append-only audit event.
     */
    @Transactional
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> metadata) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        Map<String, Object> redacted = redactMetadata(metadata);

        AuditLog log = new AuditLog(actorId, action, entityType, entityId, redacted, correlationId, null);
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> searchAuditLogs(
            UUID actorId, String action, String entityType, UUID entityId,
            Instant fromTime, Instant toTime, Pageable pageable
    ) {
        return auditLogRepository.findWithFilters(actorId, action, entityType, entityId, fromTime, toTime, pageable)
                .map(AuditLogDto::fromEntity);
    }

    private Map<String, Object> redactMetadata(Map<String, Object> original) {
        if (original == null) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            if (entry.getKey() != null && SENSITIVE_KEYS.contains(entry.getKey().toLowerCase())) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }
}
