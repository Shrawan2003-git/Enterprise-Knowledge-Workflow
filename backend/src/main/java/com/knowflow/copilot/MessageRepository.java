package com.knowflow.copilot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
