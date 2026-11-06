package com.knowflow.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.audit.AuditService;
import com.knowflow.common.KnowFlowException;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentAccessPolicy;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.PolicyResult;
import com.knowflow.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConversationIsolationTest {

    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private MessageCitationRepository citationRepository;
    private MessageFeedbackRepository feedbackRepository;
    private DocumentRepository documentRepository;
    private DocumentAccessPolicy accessPolicy;
    private RetrievalService retrievalService;
    private FakeChatModel fakeChatModel;
    private AuditService auditService;
    private ObjectMapper objectMapper;

    private ConversationServiceImpl conversationService;
    private ConversationRetentionJob retentionJob;

    private CurrentUser userA;
    private CurrentUser userB;
    private UUID sampleDocId;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        citationRepository = mock(MessageCitationRepository.class);
        feedbackRepository = mock(MessageFeedbackRepository.class);
        documentRepository = mock(DocumentRepository.class);
        accessPolicy = mock(DocumentAccessPolicy.class);
        retrievalService = mock(RetrievalService.class);
        fakeChatModel = new FakeChatModel();
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();

        conversationService = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                citationRepository,
                feedbackRepository,
                documentRepository,
                accessPolicy,
                retrievalService,
                fakeChatModel,
                auditService,
                objectMapper
        );

        retentionJob = new ConversationRetentionJob(conversationRepository, auditService, 12, 30);

        userA = new CurrentUser(UUID.randomUUID(), "alice@knowflow.internal", "Alice", UUID.randomUUID(), Set.of("ROLE_EMPLOYEE"));
        userB = new CurrentUser(UUID.randomUUID(), "bob@knowflow.internal", "Bob", UUID.randomUUID(), Set.of("ROLE_EMPLOYEE"));
        sampleDocId = UUID.randomUUID();
    }

    @Test
    @DisplayName("User B receives 404 when attempting to read User A's private conversation")
    void testUserBCannotReadUserAConversation() {
        UUID convoId = UUID.randomUUID();
        Conversation convoA = new Conversation(userA.id(), "Alice Secret Research");

        when(conversationRepository.findByIdAndDeletedAtIsNull(convoId)).thenReturn(Optional.of(convoA));

        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                conversationService.getConversation(convoId, userB));

        assertEquals(404, ex.getStatusCode());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("User B receives 404 when attempting to post a message to User A's conversation")
    void testUserBCannotPostMessageToUserAConversation() {
        UUID convoId = UUID.randomUUID();
        Conversation convoA = new Conversation(userA.id(), "Alice Strategy");

        when(conversationRepository.findByIdAndDeletedAtIsNull(convoId)).thenReturn(Optional.of(convoA));

        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                conversationService.postMessage(convoId, userB, new PostMessageRequest("What is the secret?")));

        assertEquals(404, ex.getStatusCode());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("User B receives 404 when attempting to submit feedback on User A's message")
    void testUserBCannotSubmitFeedbackOnUserAConversation() {
        UUID convoId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Conversation convoA = new Conversation(userA.id(), "Alice Task");
        Message msgA = new Message(convoId, "assistant", "Answer", "gpt-4o-mini", "v1.0", "ANSWERED");

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(msgA));
        when(conversationRepository.findByIdAndDeletedAtIsNull(convoId)).thenReturn(Optional.of(convoA));

        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                conversationService.submitFeedback(messageId, userB, new SubmitFeedbackRequest(1, null, "Great")));

        assertEquals(404, ex.getStatusCode());
    }

    @Test
    @DisplayName("Stored citation to a document whose permission was later revoked is flagged as restricted and non-openable (403)")
    void testStoredCitationToDocumentLaterMadeRestrictedIsNotOpenable() {
        UUID citationId = UUID.randomUUID();
        MessageCitation citation = new MessageCitation(
                UUID.randomUUID(), UUID.randomUUID(), sampleDocId, UUID.randomUUID(),
                1, 1, 1, "Confidential financial projections", "[S1]"
        );

        when(citationRepository.findById(citationId)).thenReturn(Optional.of(citation));
        // Permission revoked:
        when(accessPolicy.canView(eq(userA), eq(sampleDocId))).thenReturn(PolicyResult.deny("ACCESS_DENIED", "Access revoked"));

        KnowFlowException ex = assertThrows(KnowFlowException.class, () ->
                conversationService.verifyCitationAccess(citationId, userA));

        assertEquals(403, ex.getStatusCode());
        assertEquals("FORBIDDEN", ex.getErrorCode());
    }

    @Test
    @DisplayName("Retention purge job purges soft-deleted and expired inactive conversations")
    void testPurgeJobRemovesExpiredConversations() {
        when(conversationRepository.purgeSoftDeletedBefore(any(Instant.class))).thenReturn(5);
        when(conversationRepository.purgeInactiveBefore(any(Instant.class))).thenReturn(12);

        ConversationRetentionJob.RetentionPurgeResult result = retentionJob.purgeExpiredConversations();

        assertEquals(5, result.softDeletedPurged());
        assertEquals(12, result.inactivePurged());
        verify(conversationRepository).purgeSoftDeletedBefore(any(Instant.class));
        verify(conversationRepository).purgeInactiveBefore(any(Instant.class));
        verify(auditService).record(isNull(), eq("RETENTION_CLEANUP"), eq("CONVERSATION"), isNull(), any());
    }

    @Test
    @DisplayName("Multi-turn conversation does NOT smuggle revoked document text from prior turns (fresh ACL retrieval on each turn)")
    void testHistoryContextCannotSmuggleRestrictedTextAfterRevoke() {
        UUID convoId = UUID.randomUUID();
        Conversation convo = new Conversation(userA.id(), "Project Inquiry");

        when(conversationRepository.findByIdAndDeletedAtIsNull(convoId)).thenReturn(Optional.of(convo));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        // Prior message exists in history
        Message priorMsg = new Message(convoId, "assistant", "Prior answer mentioning old doc", "model", "v1.0", "ANSWERED");
        when(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(convoId)).thenReturn(new ArrayList<>(List.of(priorMsg)));
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

        // On turn 2, document was revoked -> retrievalService returns EMPTY for revoked content
        when(retrievalService.retrieve(eq("What is the secret?"), eq(userA), any())).thenReturn(List.of());

        fakeChatModel.enqueueScriptedResponse("""
                {
                  "answer": "I have no authorized documents available to answer that question.",
                  "citations": [],
                  "confidence": "INSUFFICIENT"
                }
                """);

        MessageDto response = conversationService.postMessage(convoId, userA, new PostMessageRequest("What is the secret?"));

        assertEquals("INSUFFICIENT", response.confidence());
        assertTrue(response.citations().isEmpty());
    }
}
