package com.knowflow.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.versionId = :versionId AND c.pipelineVersion = :pipelineVersion")
    void deleteByVersionIdAndPipelineVersion(@Param("versionId") UUID versionId, @Param("pipelineVersion") String pipelineVersion);

    List<DocumentChunk> findByVersionIdAndPipelineVersionOrderByChunkIndexAsc(UUID versionId, String pipelineVersion);

    long countByVersionIdAndPipelineVersion(UUID versionId, String pipelineVersion);

    List<DocumentChunk> findByVersionIdOrderByChunkIndexAsc(UUID versionId);

    @Query("SELECT c FROM DocumentChunk c WHERE c.versionId IN :versionIds AND LOWER(c.text) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<DocumentChunk> searchInVersions(@Param("query") String query, @Param("versionIds") List<UUID> versionIds);
}
