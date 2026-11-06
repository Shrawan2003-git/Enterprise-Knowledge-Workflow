package com.knowflow.copilot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbedding, UUID> {

    @Modifying
    @Query("DELETE FROM ChunkEmbedding ce WHERE ce.chunkId IN :chunkIds")
    void deleteByChunkIdIn(@Param("chunkIds") List<UUID> chunkIds);

    Optional<ChunkEmbedding> findByChunkIdAndEmbeddingModelAndPipelineVersion(
            UUID chunkId, String embeddingModel, String pipelineVersion
    );

    List<ChunkEmbedding> findByChunkIdIn(List<UUID> chunkIds);
}
