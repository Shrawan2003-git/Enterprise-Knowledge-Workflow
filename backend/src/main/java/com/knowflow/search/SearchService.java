package com.knowflow.search;

import com.knowflow.documents.DocumentSummaryDto;
import com.knowflow.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pluggable Search Service interface.
 * Phase 3 implements metadata & full-text keyword retrieval.
 * Future Phase 4 & 5 inject chunk and semantic vector sources behind this same interface.
 */
public interface SearchService {

    Page<DocumentSearchResult> search(
            String query,
            UUID categoryId,
            String tag,
            UUID departmentId,
            String status,
            UUID ownerId,
            String classification,
            Instant fromDate,
            Instant toDate,
            CurrentUser user,
            Pageable pageable
    );

    List<ChunkSearchResult> searchChunks(String query, CurrentUser user, int limit);

    record DocumentSearchResult(
            DocumentSummaryDto document,
            String snippet,
            double relevanceScore
    ) {}

    record ChunkSearchResult(
            UUID chunkId,
            UUID documentId,
            String documentTitle,
            UUID versionId,
            int versionNumber,
            int pageStart,
            int pageEnd,
            String snippet,
            double relevanceScore
    ) {}
}
