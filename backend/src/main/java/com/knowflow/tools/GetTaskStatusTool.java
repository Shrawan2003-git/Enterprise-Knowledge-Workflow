package com.knowflow.tools;

import com.knowflow.security.CurrentUser;
import com.knowflow.tasks.Task;
import com.knowflow.tasks.TaskRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class GetTaskStatusTool implements ToolDefinition {

    private final TaskRepository taskRepository;

    public GetTaskStatusTool(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public String getName() {
        return "get_task_status";
    }

    @Override
    public String getDescription() {
        return "Retrieves the status, priority and due date for a specific task assigned to or created by the user.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "required", java.util.List.of("taskId"),
                "properties", Map.of(
                        "taskId", Map.of("type", "string", "format", "uuid", "description", "The UUID of the task")
                )
        );
    }

    public record TaskStatusItem(
            UUID taskId,
            String title,
            String status,
            String priority,
            Instant dueAt,
            String completionNote
    ) {}

    @Override
    public ToolExecutionResult execute(Map<String, Object> rawArgs, CurrentUser user) {
        if (user == null) {
            return ToolExecutionResult.failure("User authentication is required");
        }

        if (rawArgs == null || !rawArgs.containsKey("taskId")) {
            return ToolExecutionResult.failure("Missing required argument: taskId");
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(rawArgs.get("taskId").toString().trim());
        } catch (Exception e) {
            return ToolExecutionResult.failure("Malformed argument: taskId must be a valid UUID");
        }

        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ToolExecutionResult.failure("Task not found: " + taskId);
        }

        // Strict authorization check: Only assignee, creator, or admin can view
        if (!user.isAdmin() && !user.id().equals(task.getAssigneeId()) && !user.id().equals(task.getCreatorId())) {
            return ToolExecutionResult.failure("ACCESS_DENIED: You are not authorized to view task " + taskId);
        }

        return ToolExecutionResult.success(new TaskStatusItem(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getDueAt(),
                task.getCompletionNote()
        ));
    }
}
