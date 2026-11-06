package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/copilot")
@Tag(name = "Copilot Conversations", description = "Conversation history, message threads and feedback APIs")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a new conversation thread")
    public ResponseEntity<ConversationDetailDto> createConversation(@Valid @RequestBody(required = false) CreateConversationRequest request) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.createConversation(user, request));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List current user's conversations")
    public ResponseEntity<List<ConversationSummaryDto>> listMyConversations() {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(conversationService.listMyConversations(user));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation details, message history and citations")
    public ResponseEntity<ConversationDetailDto> getConversation(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(conversationService.getConversation(id, user));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Soft-delete a conversation")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        conversationService.deleteConversation(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Post a message to conversation thread and receive grounded copilot answer")
    public ResponseEntity<MessageDto> postMessage(
            @PathVariable UUID id,
            @Valid @RequestBody PostMessageRequest request
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(conversationService.postMessage(id, user, request));
    }

    @PostMapping("/messages/{messageId}/feedback")
    @Operation(summary = "Submit quality feedback on an AI message")
    public ResponseEntity<FeedbackDto> submitFeedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(conversationService.submitFeedback(messageId, user, request));
    }

    @GetMapping("/citations/{citationId}/verify")
    @Operation(summary = "Verify current user access to a citation document")
    public ResponseEntity<CitationDto> verifyCitationAccess(@PathVariable UUID citationId) {
        CurrentUser user = SecurityUtils.getRequiredCurrentUser();
        return ResponseEntity.ok(conversationService.verifyCitationAccess(citationId, user));
    }
}
