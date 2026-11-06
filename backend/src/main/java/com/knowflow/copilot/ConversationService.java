package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;

import java.util.List;
import java.util.UUID;

public interface ConversationService {

    ConversationDetailDto createConversation(CurrentUser user, CreateConversationRequest request);

    ConversationDetailDto getConversation(UUID conversationId, CurrentUser user);

    List<ConversationSummaryDto> listMyConversations(CurrentUser user);

    void deleteConversation(UUID conversationId, CurrentUser user);

    MessageDto postMessage(UUID conversationId, CurrentUser user, PostMessageRequest request);

    FeedbackDto submitFeedback(UUID messageId, CurrentUser user, SubmitFeedbackRequest request);

    CitationDto verifyCitationAccess(UUID citationId, CurrentUser user);
}
