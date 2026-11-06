package com.knowflow.copilot;

import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.ingestion.DocumentChunk;
import com.knowflow.ingestion.DocumentChunkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
class VectorSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final DocumentChunkRepository chunkRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;

    VectorSearchRepository(
            DocumentChunkRepository chunkRepository,
            ChunkEmbeddingRepository embeddingRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * Package-private scoped query enforcing ACL + status=APPROVED inside SQL.
     * Never permits unrestricted access; strictly requires AccessScope.
     */
    List<UUID> findAuthorizedApprovedVersionIds(AccessScope scope) {
        if (scope == null || (scope.userId() == null && !scope.isAdmin())) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT v.id FROM Document d JOIN d.currentVersion v ")
            .append("WHERE d.status = 'APPROVED' ");

        if (!scope.isAdmin()) {
            jpql.append("AND (")
                .append("d.ownerId = :userId ")
                .append("OR UPPER(d.classification) = 'PUBLIC' ")
                .append("OR (UPPER(d.classification) = 'INTERNAL' AND (")
                .append("    d.departmentId = :departmentId ")
                .append("    OR EXISTS (")
                .append("        SELECT 1 FROM DocumentPermission p ")
                .append("        WHERE p.documentId = d.id ")
                .append("          AND ((p.principalType = 'USER' AND p.principalId = :userId) ")
                .append("               OR (p.principalType = 'DEPARTMENT' AND p.principalId = :departmentId))")
                .append("    )")
                .append(")) ")
                .append("OR (UPPER(d.classification) IN ('CONFIDENTIAL', 'RESTRICTED') AND EXISTS (")
                .append("    SELECT 1 FROM DocumentPermission p ")
                .append("    WHERE p.documentId = d.id ")
                .append("      AND ((p.principalType = 'USER' AND p.principalId = :userId) ")
                .append("           OR (p.principalType = 'DEPARTMENT' AND p.principalId = :departmentId))")
                .append("))")
                .append(") ");
        }

        TypedQuery<UUID> query = entityManager.createQuery(jpql.toString(), UUID.class);
        if (!scope.isAdmin()) {
            query.setParameter("userId", scope.userId() != null ? scope.userId() : UUID.randomUUID());
            query.setParameter("departmentId", scope.departmentId() != null ? scope.departmentId() : UUID.randomUUID());
        }

        return query.getResultList();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "who", "what", "where", "when", "how", "which",
            "this", "that", "these", "those", "from", "are", "was", "were", "been"
    );

    /**
     * Package-private scoped query for sparse (keyword/FTS) chunk retrieval.
     */
    List<ScopedChunkResult> searchChunksSparse(AccessScope scope, String query, int limit) {
        List<UUID> versionIds = findAuthorizedApprovedVersionIds(scope);
        if (versionIds.isEmpty()) {
            return List.of();
        }

        List<String> keywords = query != null ? Arrays.stream(query.toLowerCase().split("\\W+"))
                .filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w))
                .distinct()
                .limit(5)
                .toList() : List.of();

        StringBuilder jpql = new StringBuilder(
                "SELECT c FROM DocumentChunk c WHERE c.versionId IN :versionIds AND c.pipelineVersion = :pipelineVersion ");

        if (!keywords.isEmpty()) {
            jpql.append("AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) jpql.append(" OR ");
                jpql.append("LOWER(c.text) LIKE :kw").append(i).append(" ");
            }
            jpql.append(") ");
        }
        jpql.append("ORDER BY c.chunkIndex ASC");

        TypedQuery<DocumentChunk> q = entityManager.createQuery(jpql.toString(), DocumentChunk.class);
        q.setParameter("versionIds", versionIds);
        q.setParameter("pipelineVersion", scope.pipelineVersion());
        for (int i = 0; i < keywords.size(); i++) {
            q.setParameter("kw" + i, "%" + keywords.get(i) + "%");
        }
        q.setMaxResults(limit);

        List<DocumentChunk> chunks = q.getResultList();
        return mapChunksToResults(chunks, null);
    }

    /**
     * Package-private scoped query for candidate chunks and their embeddings for vector search.
     */
    List<ScopedChunkResult> searchChunksDense(AccessScope scope, int limit) {
        List<UUID> versionIds = findAuthorizedApprovedVersionIds(scope);
        if (versionIds.isEmpty()) {
            return List.of();
        }

        String jpql = "SELECT c, ce.embedding FROM DocumentChunk c, ChunkEmbedding ce " +
                "WHERE c.id = ce.chunkId AND c.versionId IN :versionIds " +
                "AND c.pipelineVersion = :pipelineVersion AND ce.pipelineVersion = :pipelineVersion";

        TypedQuery<Object[]> q = entityManager.createQuery(jpql, Object[].class);
        q.setParameter("versionIds", versionIds);
        q.setParameter("pipelineVersion", scope.pipelineVersion());
        q.setMaxResults(limit);

        List<Object[]> rows = q.getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> docIds = new HashSet<>();
        Set<UUID> verIds = new HashSet<>();
        for (Object[] row : rows) {
            DocumentChunk c = (DocumentChunk) row[0];
            docIds.add(c.getDocumentId());
            verIds.add(c.getVersionId());
        }

        Map<UUID, Document> docMap = new HashMap<>();
        documentRepository.findAllById(docIds).forEach(d -> docMap.put(d.getId(), d));

        Map<UUID, DocumentVersion> verMap = new HashMap<>();
        versionRepository.findAllById(verIds).forEach(v -> verMap.put(v.getId(), v));

        List<ScopedChunkResult> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            DocumentChunk c = (DocumentChunk) row[0];
            String emb = (String) row[1];

            Document doc = docMap.get(c.getDocumentId());
            DocumentVersion ver = verMap.get(c.getVersionId());

            String title = doc != null ? doc.getTitle() : "Document";
            int versionNumber = ver != null ? ver.getVersionNumber() : 1;

            results.add(new ScopedChunkResult(
                    c.getId(), c.getVersionId(), c.getDocumentId(), title, versionNumber,
                    c.getPageStart(), c.getPageEnd(), c.getText(), c.getTokenCount(), emb
            ));
        }

        return results;
    }

    private List<ScopedChunkResult> mapChunksToResults(List<DocumentChunk> chunks, String defaultEmb) {
        if (chunks.isEmpty()) return List.of();

        Set<UUID> docIds = new HashSet<>();
        Set<UUID> verIds = new HashSet<>();
        for (DocumentChunk c : chunks) {
            docIds.add(c.getDocumentId());
            verIds.add(c.getVersionId());
        }

        Map<UUID, Document> docMap = new HashMap<>();
        documentRepository.findAllById(docIds).forEach(d -> docMap.put(d.getId(), d));

        Map<UUID, DocumentVersion> verMap = new HashMap<>();
        versionRepository.findAllById(verIds).forEach(v -> verMap.put(v.getId(), v));

        List<ScopedChunkResult> list = new ArrayList<>(chunks.size());
        for (DocumentChunk c : chunks) {
            Document doc = docMap.get(c.getDocumentId());
            DocumentVersion ver = verMap.get(c.getVersionId());

            String title = doc != null ? doc.getTitle() : "Document";
            int versionNumber = ver != null ? ver.getVersionNumber() : 1;

            list.add(new ScopedChunkResult(
                    c.getId(), c.getVersionId(), c.getDocumentId(), title, versionNumber,
                    c.getPageStart(), c.getPageEnd(), c.getText(), c.getTokenCount(), defaultEmb
            ));
        }
        return list;
    }

    record ScopedChunkResult(
            UUID chunkId,
            UUID versionId,
            UUID documentId,
            String title,
            int versionNumber,
            int pageStart,
            int pageEnd,
            String text,
            int tokenCount,
            String embedding
    ) {}
}
