package com.knowflow.copilot;

import com.knowflow.documents.DocumentAccessPolicy;
import com.knowflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    private final VectorSearchRepository vectorSearchRepository;
    private final EmbeddingService embeddingService;
    private final DocumentAccessPolicy accessPolicy;
    private final String pipelineVersion;

    public RetrievalServiceImpl(
            VectorSearchRepository vectorSearchRepository,
            EmbeddingService embeddingService,
            DocumentAccessPolicy accessPolicy,
            @Value("${knowflow.ingestion.pipeline-version:v1}") String pipelineVersion
    ) {
        this.vectorSearchRepository = vectorSearchRepository;
        this.embeddingService = embeddingService;
        this.accessPolicy = accessPolicy;
        this.pipelineVersion = pipelineVersion;
    }

    @Override
    public List<RetrievedPassage> retrieve(String question, CurrentUser user, RetrievalOptions options) {
        if (question == null || question.isBlank() || user == null) {
            return List.of();
        }

        RetrievalOptions opts = options != null ? options : RetrievalOptions.defaults();
        AccessScope scope = AccessScope.fromUser(user, pipelineVersion);

        // 1. Sparse Search (Keyword / FTS inside SQL with strict AccessScope)
        List<VectorSearchRepository.ScopedChunkResult> sparseRows =
                vectorSearchRepository.searchChunksSparse(scope, question, 100);
        Map<UUID, Integer> sparseRanks = new HashMap<>();
        Map<UUID, RetrievedPassage> passageById = new HashMap<>();

        for (int rank = 0; rank < sparseRows.size(); rank++) {
            VectorSearchRepository.ScopedChunkResult row = sparseRows.get(rank);
            sparseRanks.put(row.chunkId(), rank + 1);
            passageById.put(row.chunkId(), new RetrievedPassage(
                    row.chunkId(), row.versionId(), row.documentId(), row.title(),
                    row.versionNumber(), row.pageStart(), row.pageEnd(), row.text(),
                    0.0, row.tokenCount()
            ));
        }

        // 2. Dense Vector Search (pgvector candidate scan inside SQL + Cosine Similarity)
        List<Float> queryVector = embeddingService.embed(question);
        List<VectorSearchRepository.ScopedChunkResult> denseRows =
                vectorSearchRepository.searchChunksDense(scope, 200);

        List<ScoredChunk> denseScored = new ArrayList<>(denseRows.size());
        for (VectorSearchRepository.ScopedChunkResult row : denseRows) {
            List<Float> chunkVec = ChunkEmbedding.stringToVector(row.embedding());
            double similarity = computeCosineSimilarity(queryVector, chunkVec);

            // Minimum semantic relevance threshold for vector similarity candidates
            if (similarity >= 0.20) {
                passageById.putIfAbsent(row.chunkId(), new RetrievedPassage(
                        row.chunkId(), row.versionId(), row.documentId(), row.title(),
                        row.versionNumber(), row.pageStart(), row.pageEnd(), row.text(),
                        0.0, row.tokenCount()
                ));
                denseScored.add(new ScoredChunk(row.chunkId(), similarity));
            }
        }

        denseScored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        Map<UUID, Integer> denseRanks = new HashMap<>();
        for (int rank = 0; rank < denseScored.size(); rank++) {
            denseRanks.put(denseScored.get(rank).chunkId(), rank + 1);
        }

        // 3. Reciprocal Rank Fusion (RRF) over matched candidates only
        Set<UUID> candidateChunkIds = new HashSet<>();
        candidateChunkIds.addAll(sparseRanks.keySet());
        candidateChunkIds.addAll(denseRanks.keySet());

        final int k = 60;
        Map<UUID, Double> rrfScores = new HashMap<>();
        for (UUID chunkId : candidateChunkIds) {
            double score = 0.0;
            if (sparseRanks.containsKey(chunkId)) {
                score += 1.0 / (k + sparseRanks.get(chunkId));
            }
            if (denseRanks.containsKey(chunkId)) {
                score += 1.0 / (k + denseRanks.get(chunkId));
            }
            rrfScores.put(chunkId, score);
        }

        // 4. Sort by RRF score descending
        List<RetrievedPassage> sortedPassages = rrfScores.entrySet().stream()
                .filter(e -> e.getValue() >= opts.minScoreThreshold())
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievedPassage p = passageById.get(e.getKey());
                    return new RetrievedPassage(
                            p.chunkId(), p.versionId(), p.documentId(), p.documentTitle(),
                            p.versionNumber(), p.pageStart(), p.pageEnd(), p.text(),
                            e.getValue(), p.tokenCount()
                    );
                })
                .toList();

        // 5. Defense-in-depth permission re-verification + token budgeting
        List<RetrievedPassage> finalPassages = new ArrayList<>();
        int accumulatedTokens = 0;

        for (RetrievedPassage passage : sortedPassages) {
            // Re-verify current permissions at query time (defense-in-depth)
            if (!accessPolicy.canView(user, passage.documentId()).allowed()) {
                log.warn("Query-time permission re-check denied access to documentId={} for user={}",
                        passage.documentId(), user.id());
                continue;
            }

            if (accumulatedTokens + passage.tokenCount() > opts.maxTokens() && !finalPassages.isEmpty()) {
                break;
            }

            finalPassages.add(passage);
            accumulatedTokens += passage.tokenCount();

            if (finalPassages.size() >= opts.topK()) {
                break;
            }
        }

        log.debug("Retrieved {} passages for query='{}', user={}", finalPassages.size(), question, user.id());
        return finalPassages;
    }

    private double computeCosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty() || v1.size() != v2.size()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            float a = v1.get(i);
            float b = v2.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(UUID chunkId, double score) {}
}
