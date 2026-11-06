package com.knowflow.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.audit.AuditService;
import com.knowflow.common.CorrelationIdFilter;
import com.knowflow.common.KnowFlowException;
import com.knowflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ToolOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ToolOrchestrator.class);

    private final ToolRegistry toolRegistry;
    private final ToolAuditLogRepository toolAuditRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final int maxIterations;
    private final int perUserHourlyQuota;

    public ToolOrchestrator(
            ToolRegistry toolRegistry,
            ToolAuditLogRepository toolAuditRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${knowflow.tools.max-iterations:3}") int maxIterations,
            @Value("${knowflow.tools.per-user-hourly-quota:50}") int perUserHourlyQuota
    ) {
        this.toolRegistry = toolRegistry;
        this.toolAuditRepository = toolAuditRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.maxIterations = maxIterations;
        this.perUserHourlyQuota = perUserHourlyQuota;
    }

    public ToolExecutionResult executeToolCall(String toolName, Map<String, Object> rawArgs, CurrentUser user, int currentIteration) {
        if (user == null) {
            throw new KnowFlowException("UNAUTHENTICATED", "User authentication required for tool execution", 401);
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // 1. Enforce tool loop iteration limit
        if (currentIteration > maxIterations) {
            recordAudit(user.id(), toolName, rawArgs, "LOOP_LIMIT_EXCEEDED", correlationId);
            throw new KnowFlowException("TOOL_LOOP_LIMIT", "Maximum tool iteration limit (" + maxIterations + ") exceeded", 429);
        }

        // 2. Enforce per-user hourly quota
        long recentCalls = toolAuditRepository.countByActorIdAndCreatedAtAfter(user.id(), Instant.now().minus(1, ChronoUnit.HOURS));
        if (recentCalls >= perUserHourlyQuota) {
            recordAudit(user.id(), toolName, rawArgs, "RATE_LIMITED", correlationId);
            throw new KnowFlowException("TOOL_QUOTA_EXCEEDED", "Hourly tool execution quota (" + perUserHourlyQuota + ") exceeded", 429);
        }

        // 3. Reject unknown tool names
        ToolDefinition tool = toolRegistry.getTool(toolName).orElse(null);
        if (tool == null) {
            recordAudit(user.id(), toolName != null ? toolName : "unknown", rawArgs, "UNKNOWN_TOOL", correlationId);
            return ToolExecutionResult.failure("UNKNOWN_TOOL: '" + toolName + "' is not in the allowlisted tool registry");
        }

        // 4. Execute tool under strict boundary
        try {
            ToolExecutionResult result = tool.execute(rawArgs, user);
            String outcome = result.success() ? "SUCCESS" : (result.errorMessage() != null && result.errorMessage().contains("ACCESS_DENIED") ? "FORBIDDEN" : "FAILED");
            recordAudit(user.id(), tool.getName(), rawArgs, outcome, correlationId);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed unexpectedly: {}", e.getMessage(), e);
            recordAudit(user.id(), tool.getName(), rawArgs, "ERROR", correlationId);
            return ToolExecutionResult.failure("Internal tool error: " + e.getMessage());
        }
    }

    private void recordAudit(UUID actorId, String toolName, Map<String, Object> rawArgs, String outcome, String correlationId) {
        String argsSummary = sanitizeArgs(rawArgs);
        ToolAuditLog auditRow = new ToolAuditLog(actorId, toolName, argsSummary, outcome, correlationId);
        toolAuditRepository.save(auditRow);

        auditService.record(
                actorId,
                "TOOL_EXECUTION_" + outcome,
                "TOOL",
                null,
                Map.of("tool", toolName != null ? toolName : "unknown", "outcome", outcome)
        );
    }

    private String sanitizeArgs(Map<String, Object> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return "{}";
        }
        try {
            Map<String, Object> sanitized = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawArgs.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("password") || key.contains("token") || key.contains("secret")) {
                    sanitized.put(entry.getKey(), "[REDACTED]");
                } else {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            }
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            return "{error: 'serialization_failed'}";
        }
    }

    public int getMaxIterations() {
        return maxIterations;
    }
}
