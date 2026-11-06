package com.knowflow.reporting;

import com.knowflow.approvals.ApprovalRequestRepository;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.tasks.TaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final DocumentRepository documentRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final TaskRepository taskRepository;

    public ReportingController(
            DocumentRepository documentRepository,
            ApprovalRequestRepository approvalRepository,
            TaskRepository taskRepository
    ) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('KNOWLEDGE_MANAGER', 'ADMIN', 'APPROVER')")
    public ResponseEntity<Map<String, Object>> getOverview() {
        long totalDocs = documentRepository.count();
        long openTasks = taskRepository.countByStatus("OPEN");
        long inProgressTasks = taskRepository.countByStatus("IN_PROGRESS");
        long overdueTasks = taskRepository.countByDueAtBeforeAndStatusNotIn(Instant.now(), List.of("DONE", "CANCELLED"));

        return ResponseEntity.ok(Map.of(
                "documents", Map.of(
                        "totalCount", totalDocs
                ),
                "tasks", Map.of(
                        "openCount", openTasks,
                        "inProgressCount", inProgressTasks,
                        "overdueCount", overdueTasks
                ),
                "ingestion", Map.of(
                        "failuresCount", 0, // Stubbed until Phase 4 ingestion jobs run
                        "status", "READY"
                )
        ));
    }
}
