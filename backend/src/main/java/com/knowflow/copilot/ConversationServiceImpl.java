package com.knowflow.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.audit.AuditService;
import com.knowflow.common.KnowFlowException;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentAccessPolicy;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAccessPolicy accessPolicy;
    private final RetrievalService retrievalService;
    private final ChatModelGateway chatModelGateway;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${knowflow.admin.conversation-access.enabled:false}")
    private boolean adminAccessEnabled;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            MessageFeedbackRepository feedbackRepository,
            DocumentRepository documentRepository,
            DocumentAccessPolicy accessPolicy,
            RetrievalService retrievalService,
            ChatModelGateway chatModelGateway,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.feedbackRepository = feedbackRepository;
        this.documentRepository = documentRepository;
        this.accessPolicy = accessPolicy;
        this.retrievalService = retrievalService;
        this.chatModelGateway = chatModelGateway;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ConversationDetailDto createConversation(CurrentUser user, CreateConversationRequest request) {
        String title = request != null && request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : "New Conversation";

        Conversation conversation = new Conversation(user.id(), title);
        conversation = conversationRepository.save(conversation);

        return new ConversationDetailDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                List.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDetailDto getConversation(UUID conversationId, CurrentUser user) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Conversation not found", 404));

        verifyConversationAccess(conversation, user);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<MessageDto> messageDtos = new ArrayList<>(messages.size());

        for (Message msg : messages) {
            List<MessageCitation> citations = citationRepository.findByMessageId(msg.getId());
            List<CitationDto> citationDtos = citations.stream().map(c -> {
                boolean canAccess = accessPolicy.canView(user, c.getDocumentId()).allowed();
                String title = documentRepository.findById(c.getDocumentId()).map(Document::getTitle).orElse("Document");
                return new CitationDto(
                        c.getId(),
                        c.getSourceId(),
                        c.getDocumentId(),
                        title,
                        c.getVersionNumber(),
                        c.getPageStart(),
                        c.getPageEnd(),
                        CopilotCitation.formatPageHint(c.getPageStart(), c.getPageEnd()),
                        c.getSnippet(),
                        canAccess
                );
            }).toList();

            FeedbackDto feedbackDto = feedbackRepository.findByMessageIdAndUserId(msg.getId(), user.id())
                    .map(f -> new FeedbackDto(f.getId(), f.getRating(), f.getReasonCode(), f.getComment(), f.getCreatedAt()))
                    .orElse(null);

            messageDtos.add(new MessageDto(
                    msg.getId(),
                    msg.getConversationId(),
                    msg.getRole(),
                    msg.getContent(),
                    msg.getModel(),
                    msg.getPromptVersion(),
                    msg.getConfidence(),
                    msg.getCreatedAt(),
                    citationDtos,
                    feedbackDto
            ));
        }

        return new ConversationDetailDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messageDtos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listMyConversations(CurrentUser user) {
        List<Conversation> list = conversationRepository.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.id());
        List<ConversationSummaryDto> result = new ArrayList<>(list.size());

        for (Conversation c : list) {
            List<Message> msgs = messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getId());
            result.add(new ConversationSummaryDto(
                    c.getId(),
                    c.getTitle(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    msgs.size()
            ));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteConversation(UUID conversationId, CurrentUser user) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Conversation not found", 404));

        if (!conversation.getOwnerId().equals(user.id())) {
            // Strict ownership isolation: return 404 to avoid disclosing existence
            throw new KnowFlowException("NOT_FOUND", "Conversation not found", 404);
        }

        conversation.markDeleted();
        conversationRepository.save(conversation);

        auditService.record(user.id(), "CONVERSATION_DELETED", "CONVERSATION", conversationId, Map.of());
    }

    @Override
    @Transactional
    public MessageDto postMessage(UUID conversationId, CurrentUser user, PostMessageRequest request) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Conversation not found", 404));

        if (!conversation.getOwnerId().equals(user.id())) {
            throw new KnowFlowException("NOT_FOUND", "Conversation not found", 404);
        }

        // Auto-update conversation title if still default
        if ("New Conversation".equalsIgnoreCase(conversation.getTitle())) {
            String snippet = request.content().trim();
            conversation.setTitle(snippet.length() > 40 ? snippet.substring(0, 37) + "..." : snippet);
        }

        // 1. Save User Message
        Message userMessage = new Message(conversationId, "user", request.content(), null, null, null);
        userMessage = messageRepository.save(userMessage);

        // 2. Load recent conversation turns for context
        List<Message> history = messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history); // chronological order

        // 3. Fresh Retrieval for current question turn (always ACL-filtered inside SQL)
        List<RetrievedPassage> passages = retrievalService.retrieve(request.content(), user, RetrievalOptions.defaults());

        // 4. Construct prompt containing conversation context and fresh untrusted evidence blocks
        StringBuilder contextBuilder = new StringBuilder();
        if (history.size() > 1) {
            contextBuilder.append("PREVIOUS CONVERSATION HISTORY (for conversational flow only):\n");
            for (Message m : history) {
                if (m.getId().equals(userMessage.getId())) continue;
                contextBuilder.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n");
            }
            contextBuilder.append("\n");
        }
        contextBuilder.append(request.content());

        String userPrompt = PromptTemplate.buildUserPrompt(contextBuilder.toString(), passages);
        String systemPrompt = PromptTemplate.SYSTEM_PROMPT;

        // 5. LLM Invocation
        ChatModelGateway.ChatResponse chatResponse = chatModelGateway.generate(systemPrompt, userPrompt);

        // 6. Parse structured response
        ParsedLlmOutput parsed = parseOutput(chatResponse.content());

        // 7. Validate citations & enforce grounding
        CitationValidator.ValidationResult validated = CitationValidator.validate(
                parsed.answer(),
                parsed.citations(),
                parsed.confidence(),
                passages
        );

        // 8. Save Assistant Message
        Message assistantMessage = new Message(
                conversationId,
                "assistant",
                validated.sanitizedAnswer(),
                chatModelGateway.getModelName(),
                PromptTemplate.PROMPT_VERSION,
                validated.confidence().name()
        );
        assistantMessage = messageRepository.save(assistantMessage);

        // 9. Save Citations
        List<CitationDto> citationDtos = new ArrayList<>();
        for (CopilotCitation c : validated.validCitations()) {
            MessageCitation citation = new MessageCitation(
                    assistantMessage.getId(),
                    null, // chunkId optional
                    c.documentId(),
                    null, // versionId optional
                    c.versionNumber(),
                    c.pageStart(),
                    c.pageEnd(),
                    c.snippet(),
                    c.sourceId()
            );
            citation = citationRepository.save(citation);

            citationDtos.add(new CitationDto(
                    citation.getId(),
                    citation.getSourceId(),
                    citation.getDocumentId(),
                    c.documentTitle(),
                    citation.getVersionNumber(),
                    citation.getPageStart(),
                    citation.getPageEnd(),
                    c.pageHint(),
                    citation.getSnippet(),
                    true
            ));
        }

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return new MessageDto(
                assistantMessage.getId(),
                conversationId,
                assistantMessage.getRole(),
                assistantMessage.getContent(),
                assistantMessage.getModel(),
                assistantMessage.getPromptVersion(),
                assistantMessage.getConfidence(),
                assistantMessage.getCreatedAt(),
                citationDtos,
                null
        );
    }

    @Override
    @Transactional
    public FeedbackDto submitFeedback(UUID messageId, CurrentUser user, SubmitFeedbackRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Message not found", 404));

        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(message.getConversationId())
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Conversation not found", 404));

        if (!conversation.getOwnerId().equals(user.id())) {
            throw new KnowFlowException("NOT_FOUND", "Message not found", 404);
        }

        if (request.rating() == null) {
            throw new KnowFlowException("BAD_REQUEST", "Rating is required", 400);
        }

        MessageFeedback feedback = feedbackRepository.findByMessageIdAndUserId(messageId, user.id())
                .map(existing -> {
                    // Update existing
                    return new MessageFeedback(messageId, user.id(), request.rating(), request.reasonCode(), request.comment());
                })
                .orElseGet(() -> new MessageFeedback(messageId, user.id(), request.rating(), request.reasonCode(), request.comment()));

        feedback = feedbackRepository.save(feedback);

        return new FeedbackDto(
                feedback.getId(),
                feedback.getRating(),
                feedback.getReasonCode(),
                feedback.getComment(),
                feedback.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CitationDto verifyCitationAccess(UUID citationId, CurrentUser user) {
        MessageCitation citation = citationRepository.findById(citationId)
                .orElseThrow(() -> new KnowFlowException("NOT_FOUND", "Citation not found", 404));

        boolean canAccess = accessPolicy.canView(user, citation.getDocumentId()).allowed();
        if (!canAccess) {
            throw new KnowFlowException("FORBIDDEN", "Source document is no longer accessible or your permission has been revoked.", 403);
        }

        String title = documentRepository.findById(citation.getDocumentId()).map(Document::getTitle).orElse("Document");

        return new CitationDto(
                citation.getId(),
                citation.getSourceId(),
                citation.getDocumentId(),
                title,
                citation.getVersionNumber(),
                citation.getPageStart(),
                citation.getPageEnd(),
                CopilotCitation.formatPageHint(citation.getPageStart(), citation.getPageEnd()),
                citation.getSnippet(),
                true
        );
    }

    private void verifyConversationAccess(Conversation conversation, CurrentUser user) {
        if (conversation.getOwnerId().equals(user.id())) {
            return;
        }

        // Admin opt-in access policy (default OFF per ADR-008)
        if (user.roles().contains("ROLE_ADMIN") && adminAccessEnabled) {
            log.info("Admin user {} accessing conversation {} owned by {}",
                    user.id(), conversation.getId(), conversation.getOwnerId());
            auditService.record(
                    user.id(),
                    "ADMIN_CONVERSATION_ACCESS",
                    "CONVERSATION",
                    conversation.getId(),
                    Map.of("ownerId", conversation.getOwnerId())
            );
            return;
        }

        // Return 404 to avoid leaking existence of foreign conversations
        throw new KnowFlowException("NOT_FOUND", "Conversation not found", 404);
    }

    record ParsedLlmOutput(String answer, List<String> citations, ConfidenceLevel confidence) {}

    private ParsedLlmOutput parseOutput(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedLlmOutput("No response generated.", List.of(), ConfidenceLevel.INSUFFICIENT);
        }

        String cleaned = rawContent.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String answer = node.has("answer") ? node.get("answer").asText() : "";
            String confStr = node.has("confidence") ? node.get("confidence").asText() : "ANSWERED";

            ConfidenceLevel confidence;
            try {
                confidence = ConfidenceLevel.valueOf(confStr.toUpperCase());
            } catch (Exception e) {
                confidence = ConfidenceLevel.ANSWERED;
            }

            List<String> citationIds = new ArrayList<>();
            if (node.has("citations") && node.get("citations").isArray()) {
                for (JsonNode cNode : node.get("citations")) {
                    if (cNode.has("sourceId")) {
                        citationIds.add(cNode.get("sourceId").asText());
                    } else if (cNode.isTextual()) {
                        citationIds.add(cNode.asText());
                    }
                }
            }

            return new ParsedLlmOutput(answer, citationIds, confidence);
        } catch (Exception e) {
            return new ParsedLlmOutput(cleaned, List.of(), ConfidenceLevel.ANSWERED);
        }
    }
}
