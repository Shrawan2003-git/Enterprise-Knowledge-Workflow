package com.knowflow.tasks;

import com.knowflow.audit.AuditService;
import com.knowflow.documents.DocumentAccessPolicy;
import com.knowflow.identity.UserRepository;
import com.knowflow.notifications.NotificationSender;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final DocumentAccessPolicy accessPolicy;
    private final NotificationSender notificationSender;
    private final AuditService auditService;

    public TaskService(
            TaskRepository taskRepository,
            TaskCommentRepository commentRepository,
            UserRepository userRepository,
            DocumentAccessPolicy accessPolicy,
            NotificationSender notificationSender,
            AuditService auditService
    ) {
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.notificationSender = notificationSender;
        this.auditService = auditService;
    }

    public TaskDto createTask(CreateTaskRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();

        // Validate creator has access to linked document if specified
        if (request.linkedDocumentId() != null) {
            if (!accessPolicy.canView(user, request.linkedDocumentId()).allowed()) {
                throw new org.springframework.security.access.AccessDeniedException("Cannot link to a document you cannot view");
            }
        }

        userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new IllegalArgumentException("Assignee user not found"));

        Task task = new Task(
                request.title(),
                request.description(),
                request.assigneeId(),
                user.id(),
                request.linkedDocumentId(),
                request.linkedApprovalId(),
                request.priority(),
                request.dueAt()
        );

        Task saved = taskRepository.save(task);

        notificationSender.sendNotification(
                request.assigneeId(),
                "TASK_ASSIGNED",
                "New Task Assigned",
                "You have been assigned: " + saved.getTitle(),
                Map.of("taskId", saved.getId()),
                "TASK_ASSIGNED_" + saved.getId()
        );

        auditService.record(user.id(), "CREATE_TASK", "TASK", saved.getId(), Map.of("title", saved.getTitle()));

        return TaskDto.fromEntity(saved);
    }

    public TaskDto updateTaskStatus(UUID taskId, UpdateTaskStatusRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));

        if (!user.isAdmin() && !user.id().equals(task.getAssigneeId()) && !user.id().equals(task.getCreatorId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only task assignee or creator can update task status");
        }

        // Optimistic locking verification
        if (task.getVersion() != request.version()) {
            throw new IllegalStateException("Task has been updated concurrently. Expected version: " + task.getVersion());
        }

        String targetStatus = request.status().toUpperCase();
        validateStatusTransition(task.getStatus(), targetStatus);

        task.setStatus(targetStatus);
        if (request.completionNote() != null) {
            task.setCompletionNote(request.completionNote());
        }
        task.setUpdatedAt(Instant.now());

        Task saved = taskRepository.save(task);

        auditService.record(user.id(), "UPDATE_TASK_STATUS", "TASK", saved.getId(),
                Map.of("newStatus", targetStatus));

        return TaskDto.fromEntity(saved);
    }

    public void addComment(UUID taskId, String content) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));

        if (!user.isAdmin() && !user.id().equals(task.getAssigneeId()) && !user.id().equals(task.getCreatorId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only assignee or creator can comment on this task");
        }

        TaskComment comment = new TaskComment(taskId, user.id(), content);
        commentRepository.save(comment);

        // Notify counterpart
        UUID notifyUser = user.id().equals(task.getAssigneeId()) ? task.getCreatorId() : task.getAssigneeId();
        notificationSender.sendNotification(
                notifyUser,
                "TASK_COMMENT",
                "New comment on task",
                "New comment added to: " + task.getTitle(),
                Map.of("taskId", taskId),
                "TASK_COMMENT_" + comment.getId()
        );
    }

    @Transactional(readOnly = true)
    public Page<TaskDto> listMyTasks(Pageable pageable) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return taskRepository.findByAssigneeId(user.id(), pageable).map(TaskDto::fromEntity);
    }

    private void validateStatusTransition(String from, String to) {
        Map<String, Set<String>> allowed = Map.of(
                "OPEN", Set.of("IN_PROGRESS", "CANCELLED"),
                "IN_PROGRESS", Set.of("DONE", "CANCELLED"),
                "DONE", Set.of(),
                "CANCELLED", Set.of()
        );

        Set<String> validTargets = allowed.getOrDefault(from, Set.of());
        if (!validTargets.contains(to)) {
            throw new IllegalArgumentException("Invalid task transition from " + from + " to " + to);
        }
    }
}
