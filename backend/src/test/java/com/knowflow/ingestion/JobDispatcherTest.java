package com.knowflow.ingestion;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class JobDispatcherTest {

    private InMemoryJobRepository jobRepository;
    private DatabaseJobDispatcher dispatcher;

    @BeforeEach
    void setup() {
        jobRepository = new InMemoryJobRepository();
        IngestionMetrics metrics = new IngestionMetrics(new SimpleMeterRegistry(), jobRepository);
        dispatcher = new DatabaseJobDispatcher(jobRepository, metrics);
        ReflectionTestUtils.setField(dispatcher, "defaultMaxAttempts", 3);
        ReflectionTestUtils.setField(dispatcher, "baseBackoffMs", 100L);
        ReflectionTestUtils.setField(dispatcher, "maxBackoffMs", 1000L);
    }

    @Test
    void duplicateEnqueueIsNoOp() {
        UUID versionId = UUID.randomUUID();
        IngestionJob initial = dispatcher.enqueue(versionId, "v1");
        assertNotNull(initial);

        IngestionJob duplicate = dispatcher.enqueue(versionId, "v1");
        assertSame(initial, duplicate);
        assertEquals(1, jobRepository.countActiveQueue());
    }

    @Test
    void twoWorkersRacingNeverDoubleProcess() {
        UUID versionId = UUID.randomUUID();
        IngestionJob job = dispatcher.enqueue(versionId, "v1");

        // Worker A claims the job
        List<IngestionJob> claimedA = dispatcher.claimBatch("worker-A", Duration.ofMinutes(1), 1);
        assertEquals(1, claimedA.size());
        assertEquals("worker-A", claimedA.get(0).getLockedBy());

        // Worker B tries to claim the same job while it's still locked by Worker A
        List<IngestionJob> claimedB = dispatcher.claimBatch("worker-B", Duration.ofMinutes(1), 1);
        assertTrue(claimedB.isEmpty(), "Worker B should not claim a job already leased to Worker A");
    }

    @Test
    void crashedWorkerJobGetsReclaimedAfterLeaseExpiry() {
        UUID versionId = UUID.randomUUID();
        IngestionJob job = dispatcher.enqueue(versionId, "v1");

        // Worker A claims with 0 duration (instantly expired lease)
        List<IngestionJob> claimedA = dispatcher.claimBatch("worker-A", Duration.ofMillis(-1000), 1);
        assertEquals(1, claimedA.size());

        // Worker B claims; since lease is in the past, Worker B successfully reclaims the crashed job
        List<IngestionJob> claimedB = dispatcher.claimBatch("worker-B", Duration.ofMinutes(1), 1);
        assertEquals(1, claimedB.size());
        assertEquals("worker-B", claimedB.get(0).getLockedBy());
    }

    @Test
    void retryExceedingMaxAttemptsFailsPermanently() {
        UUID versionId = UUID.randomUUID();
        IngestionJob job = dispatcher.enqueue(versionId, "v1");
        job.setAttempts(3);
        job.setMaxAttempts(3);

        dispatcher.scheduleRetry(job.getId(), IngestionErrorCode.PARSER_TIMEOUT, "Timeout", Duration.ofSeconds(1));

        assertEquals(IngestionStatus.FAILED.name(), job.getStatus());
        assertEquals(IngestionErrorCode.PARSER_TIMEOUT.name(), job.getErrorCode());
    }

    static class InMemoryJobRepository implements IngestionJobRepository {
        private final Map<UUID, IngestionJob> store = new ConcurrentHashMap<>();

        @Override
        public Optional<IngestionJob> findByVersionIdAndPipelineVersion(UUID versionId, String pipelineVersion) {
            return store.values().stream()
                    .filter(j -> j.getVersionId().equals(versionId) && j.getPipelineVersion().equals(pipelineVersion))
                    .findFirst();
        }

        @Override
        public List<IngestionJob> findByVersionId(UUID versionId) {
            return store.values().stream().filter(j -> j.getVersionId().equals(versionId)).toList();
        }

        @Override
        public Page<IngestionJob> findByStatus(String status, Pageable pageable) {
            List<IngestionJob> list = store.values().stream().filter(j -> j.getStatus().equalsIgnoreCase(status)).toList();
            return new PageImpl<>(list, pageable, list.size());
        }

        @Override
        public List<IngestionJob> findClaimableCandidates(Instant now, Pageable pageable) {
            return store.values().stream()
                    .filter(j -> ("PENDING".equals(j.getStatus()) || "RETRY_SCHEDULED".equals(j.getStatus()) ||
                            ("RUNNING".equals(j.getStatus()) && j.getLockedUntil() != null && j.getLockedUntil().isBefore(now))))
                    .filter(j -> j.getNextAttemptAt() == null || !j.getNextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(IngestionJob::getCreatedAt))
                    .toList();
        }

        @Override
        public int tryClaimLease(UUID jobId, String workerId, Instant now, Instant lockUntil) {
            IngestionJob job = store.get(jobId);
            if (job == null) return 0;
            if (job.getLockedUntil() != null && !job.getLockedUntil().isBefore(now)) {
                return 0; // Already leased
            }
            job.setStatus("RUNNING");
            job.setLockedBy(workerId);
            job.setLockedUntil(lockUntil);
            job.setStartedAt(now);
            job.setAttempts(job.getAttempts() + 1);
            job.setUpdatedAt(now);
            return 1;
        }

        @Override
        public int heartbeat(UUID jobId, String workerId, Instant now, Instant extendedUntil) {
            IngestionJob job = store.get(jobId);
            if (job != null && workerId.equals(job.getLockedBy()) && "RUNNING".equals(job.getStatus())) {
                job.setLockedUntil(extendedUntil);
                job.setUpdatedAt(now);
                return 1;
            }
            return 0;
        }

        @Override
        public long countActiveQueue() {
            return store.values().stream()
                    .filter(j -> Set.of("PENDING", "RETRY_SCHEDULED", "RUNNING").contains(j.getStatus()))
                    .count();
        }

        @Override
        public long countByStatus(String status) {
            return store.values().stream().filter(j -> j.getStatus().equalsIgnoreCase(status)).count();
        }

        @Override
        public <S extends IngestionJob> S save(S entity) {
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<IngestionJob> findById(UUID uuid) {
            return Optional.ofNullable(store.get(uuid));
        }

        @Override
        public boolean existsById(UUID uuid) { return store.containsKey(uuid); }

        @Override
        public List<IngestionJob> findAll() { return new ArrayList<>(store.values()); }

        @Override
        public List<IngestionJob> findAllById(Iterable<UUID> uuids) { return List.of(); }

        @Override
        public long count() { return store.size(); }

        @Override
        public void deleteById(UUID uuid) { store.remove(uuid); }

        @Override
        public void delete(IngestionJob entity) { store.remove(entity.getId()); }

        @Override
        public void deleteAllById(Iterable<? extends UUID> uuids) {}

        @Override
        public void deleteAll(Iterable<? extends IngestionJob> entities) {}

        @Override
        public void deleteAll() { store.clear(); }

        @Override
        public void flush() {}

        @Override
        public <S extends IngestionJob> S saveAndFlush(S entity) { return save(entity); }

        @Override
        public <S extends IngestionJob> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }

        @Override
        public void deleteAllInBatch(Iterable<IngestionJob> entities) {}

        @Override
        public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}

        @Override
        public void deleteAllInBatch() {}

        @Override
        public IngestionJob getOne(UUID uuid) { return store.get(uuid); }

        @Override
        public IngestionJob getById(UUID uuid) { return store.get(uuid); }

        @Override
        public IngestionJob getReferenceById(UUID uuid) { return store.get(uuid); }

        @Override
        public <S extends IngestionJob> Optional<S> findOne(Example<S> example) { return Optional.empty(); }

        @Override
        public <S extends IngestionJob> List<S> findAll(Example<S> example) { return List.of(); }

        @Override
        public <S extends IngestionJob> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }

        @Override
        public <S extends IngestionJob> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }

        @Override
        public <S extends IngestionJob> long count(Example<S> example) { return 0; }

        @Override
        public <S extends IngestionJob> boolean exists(Example<S> example) { return false; }

        @Override
        public <S extends IngestionJob, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }

        @Override
        public <S extends IngestionJob> List<S> saveAll(Iterable<S> entities) {
            entities.forEach(this::save);
            return (List<S>) findAll();
        }

        @Override
        public List<IngestionJob> findAll(Sort sort) { return findAll(); }

        @Override
        public Page<IngestionJob> findAll(Pageable pageable) {
            return new PageImpl<>(findAll(), pageable, store.size());
        }
    }
}
