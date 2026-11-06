package com.knowflow.copilot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Conversation> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Conversation> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    List<Conversation> findByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff);

    List<Conversation> findByDeletedAtIsNullAndUpdatedAtBefore(Instant cutoff);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoff")
    int purgeSoftDeletedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.deletedAt IS NULL AND c.updatedAt < :cutoff")
    int purgeInactiveBefore(@Param("cutoff") Instant cutoff);
}
