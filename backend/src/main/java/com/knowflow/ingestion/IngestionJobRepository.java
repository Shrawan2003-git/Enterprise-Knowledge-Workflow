package com.knowflow.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Optional<IngestionJob> findByVersionIdAndPipelineVersion(UUID versionId, String pipelineVersion);

    List<IngestionJob> findByVersionId(UUID versionId);

    Page<IngestionJob> findByStatus(String status, Pageable pageable);

    @Query("SELECT j FROM IngestionJob j WHERE " +
           "(j.status IN ('PENDING', 'RETRY_SCHEDULED') OR (j.status = 'RUNNING' AND j.lockedUntil < :now)) " +
           "AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now) " +
           "ORDER BY j.createdAt ASC")
    List<IngestionJob> findClaimableCandidates(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.status = 'RUNNING', j.lockedBy = :workerId, " +
           "j.lockedUntil = :lockUntil, j.startedAt = :now, j.attempts = j.attempts + 1, j.updatedAt = :now " +
           "WHERE j.id = :jobId AND (j.lockedUntil IS NULL OR j.lockedUntil < :now)")
    int tryClaimLease(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
                      @Param("now") Instant now, @Param("lockUntil") Instant lockUntil);

    @Modifying
    @Query("UPDATE IngestionJob j SET j.lockedUntil = :extendedUntil, j.updatedAt = :now " +
           "WHERE j.id = :jobId AND j.lockedBy = :workerId AND j.status = 'RUNNING'")
    int heartbeat(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
                  @Param("now") Instant now, @Param("extendedUntil") Instant extendedUntil);

    @Query("SELECT COUNT(j) FROM IngestionJob j WHERE j.status IN ('PENDING', 'RETRY_SCHEDULED', 'RUNNING')")
    long countActiveQueue();

    @Query("SELECT COUNT(j) FROM IngestionJob j WHERE j.status = :status")
    long countByStatus(@Param("status") String status);
}
