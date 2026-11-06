package com.knowflow.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.approvals.ApprovalRequest;
import com.knowflow.approvals.ApprovalRequestRepository;
import com.knowflow.audit.AuditService;
import com.knowflow.common.KnowFlowException;
import com.knowflow.security.CurrentUser;
import com.knowflow.tasks.Task;
import com.knowflow.tasks.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ToolAuthorizationTest {

    private ToolRegistry toolRegistry;
    private ToolAuditLogRepository toolAuditRepository;
    private AuditService auditService;
    private TaskRepository taskRepository;
    private ApprovalRequestRepository approvalRepository;
    private ToolOrchestrator toolOrchestrator;

    private CurrentUser alice;
    private CurrentUser bob;
    private UUID alicesTaskId;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        approvalRepository = mock(ApprovalRequestRepository.class);
        toolAuditRepository = mock(ToolAuditLogRepository.class);
        auditService = mock(AuditService.class);

        GetTaskStatusTool taskTool = new GetTaskStatusTool(taskRepository);
        GetMyPendingApprovalsTool approvalTool = new GetMyPendingApprovalsTool(approvalRepository);

        toolRegistry = new ToolRegistry(List.of(taskTool, approvalTool));
        toolOrchestrator = new ToolOrchestrator(toolRegistry, toolAuditRepository, auditService, new ObjectMapper(), 3, 50);

        alice = new CurrentUser(UUID.randomUUID(), "alice@knowflow.internal", "Alice", UUID.randomUUID(), Set.of("ROLE_EMPLOYEE"));
        bob = new CurrentUser(UUID.randomUUID(), "bob@knowflow.internal", "Bob", UUID.randomUUID(), Set.of("ROLE_EMPLOYEE"));

        alicesTaskId = UUID.randomUUID();
        Task alicesTask = new Task("Review Alice Report", "Sensitive Task", alice.id(), alice.id(), null, null, "HIGH", null);
        when(taskRepository.findById(alicesTaskId)).thenReturn(Optional.of(alicesTask));
    }

    @Test
    @DisplayName("Tool call by Bob for Alice's private task is denied with ACCESS_DENIED")
    void testToolCallForAnotherUsersTaskDenied() {
        ToolExecutionResult result = toolOrchestrator.executeToolCall(
                "get_task_status",
                Map.of("taskId", alicesTaskId.toString()),
                bob,
                1
        );

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("ACCESS_DENIED"),
                "Unauthorized user must receive ACCESS_DENIED when attempting to inspect foreign task");
        verify(toolAuditRepository).save(argThat(log -> "FORBIDDEN".equals(log.getOutcome())));
    }

    @Test
    @DisplayName("Model-supplied victim userId in tool args is strictly ignored in favor of authenticated CurrentUser")
    void testModelSuppliedUserIdIgnored() {
        UUID victimId = UUID.randomUUID();
        when(approvalRepository.findByApproverId(eq(alice.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Model injects malicious arg "userId": victimId
        ToolExecutionResult result = toolOrchestrator.executeToolCall(
                "get_my_pending_approvals",
                Map.of("userId", victimId.toString()),
                alice,
                1
        );

        assertTrue(result.success());
        // Verify repository query used alice.id(), NEVER victimId
        verify(approvalRepository).findByApproverId(eq(alice.id()), any(Pageable.class));
        verify(approvalRepository, never()).findByApproverId(eq(victimId), any(Pageable.class));
    }

    @Test
    @DisplayName("Malformed tool arguments (invalid UUID) are cleanly rejected with failure message")
    void testMalformedArgsRejected() {
        ToolExecutionResult result = toolOrchestrator.executeToolCall(
                "get_task_status",
                Map.of("taskId", "not-a-valid-uuid-1234"),
                alice,
                1
        );

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Malformed argument"));
        verify(toolAuditRepository).save(argThat(log -> "FAILED".equals(log.getOutcome())));
    }

    @Test
    @DisplayName("Non-allowlisted or unknown tool name is rejected with UNKNOWN_TOOL error")
    void testUnknownToolRejected() {
        ToolExecutionResult result = toolOrchestrator.executeToolCall(
                "execute_arbitrary_shell_command",
                Map.of("cmd", "whoami"),
                alice,
                1
        );

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("UNKNOWN_TOOL"));
        verify(toolAuditRepository).save(argThat(log -> "UNKNOWN_TOOL".equals(log.getOutcome())));
    }

    @Test
    @DisplayName("Exceeding per-user hourly tool execution quota trips 429 TOOL_QUOTA_EXCEEDED")
    void testQuotaExceededReturns429() {
        when(toolAuditRepository.countByActorIdAndCreatedAtAfter(eq(alice.id()), any(Instant.class))).thenReturn(50L);

        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                toolOrchestrator.executeToolCall("get_my_pending_approvals", Map.of(), alice, 1));

        assertEquals(429, ex.getStatusCode());
        assertEquals("TOOL_QUOTA_EXCEEDED", ex.getErrorCode());
        verify(toolAuditRepository).save(argThat(log -> "RATE_LIMITED".equals(log.getOutcome())));
    }

    @Test
    @DisplayName("Exceeding maximum allowed tool loop iterations trips 429 TOOL_LOOP_LIMIT")
    void testLoopLimitEnforced() {
        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                toolOrchestrator.executeToolCall("get_my_pending_approvals", Map.of(), alice, 4)); // max is 3

        assertEquals(429, ex.getStatusCode());
        assertEquals("TOOL_LOOP_LIMIT", ex.getErrorCode());
        verify(toolAuditRepository).save(argThat(log -> "LOOP_LIMIT_EXCEEDED".equals(log.getOutcome())));
    }

    @Test
    @DisplayName("Audit row is written for every single tool invocation with outcome and actor")
    void testAuditRowWrittenForEveryCall() {
        when(approvalRepository.findByApproverId(eq(alice.id()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new ApprovalRequest(UUID.randomUUID(), UUID.randomUUID(), alice.id())
                )));

        ToolExecutionResult result = toolOrchestrator.executeToolCall("get_my_pending_approvals", Map.of(), alice, 1);

        assertTrue(result.success());
        verify(toolAuditRepository, atLeastOnce()).save(argThat(log ->
                alice.id().equals(log.getActorId()) &&
                "get_my_pending_approvals".equals(log.getToolName()) &&
                "SUCCESS".equals(log.getOutcome())
        ));
    }
}
