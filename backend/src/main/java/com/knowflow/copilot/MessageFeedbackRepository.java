package com.knowflow.copilot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, UUID> {

    Optional<MessageFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
}
