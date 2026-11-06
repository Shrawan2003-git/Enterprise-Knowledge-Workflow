package com.knowflow.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByAssigneeId(UUID assigneeId, Pageable pageable);
    Page<Task> findByCreatorId(UUID creatorId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.dueAt < :now AND t.status NOT IN ('DONE', 'CANCELLED')")
    List<Task> findOverdueTasks(@Param("now") Instant now);

    long countByStatus(String status);
    long countByDueAtBeforeAndStatusNotIn(Instant now, List<String> terminalStatuses);
}
