package com.knowflow.copilot;

import com.knowflow.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class ConversationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);

    private final ConversationRepository conversationRepository;
    private final AuditService auditService;
    private final int retentionMonths;
    private final int deletedRetentionDays;

    public ConversationRetentionJob(
            ConversationRepository conversationRepository,
            AuditService auditService,
            @Value("${knowflow.retention.conversations-months:12}") int retentionMonths,
            @Value("${knowflow.retention.deleted-conversations-days:30}") int deletedRetentionDays
    ) {
        this.conversationRepository = conversationRepository;
        this.auditService = auditService;
        this.retentionMonths = retentionMonths;
        this.deletedRetentionDays = deletedRetentionDays;
    }

    /**
     * Executes daily at 03:00 UTC to purge expired conversations per ADR-008.
     */
    @Scheduled(cron = "${knowflow.retention.cron:0 0 3 * * *}")
    @Transactional
    public RetentionPurgeResult purgeExpiredConversations() {
        Instant deletedCutoff = Instant.now().minus(deletedRetentionDays, ChronoUnit.DAYS);
        Instant inactiveCutoff = Instant.now().minus(retentionMonths * 30L, ChronoUnit.DAYS);

        int softDeletedPurged = conversationRepository.purgeSoftDeletedBefore(deletedCutoff);
        int inactivePurged = conversationRepository.purgeInactiveBefore(inactiveCutoff);

        log.info("Retention purge completed: {} soft-deleted and {} inactive conversations purged.",
                softDeletedPurged, inactivePurged);

        auditService.record(
                null,
                "RETENTION_CLEANUP",
                "CONVERSATION",
                null,
                Map.of(
                        "softDeletedPurged", softDeletedPurged,
                        "inactivePurged", inactivePurged
                )
        );

        return new RetentionPurgeResult(softDeletedPurged, inactivePurged);
    }

    public record RetentionPurgeResult(int softDeletedPurged, int inactivePurged) {}
}
