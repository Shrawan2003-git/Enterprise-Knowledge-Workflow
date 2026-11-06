package com.knowflow.copilot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findByMessageId(UUID messageId);

    List<MessageCitation> findByMessageIdIn(List<UUID> messageIds);
}
