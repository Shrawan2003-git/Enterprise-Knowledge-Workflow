package com.knowflow.copilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chunk_embeddings")
public class ChunkEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(name = "dimensions", nullable = false)
    private int dimensions;

    @Column(name = "pipeline_version", nullable = false, length = 50)
    private String pipelineVersion = "v1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ChunkEmbedding() {}

    public ChunkEmbedding(UUID chunkId, List<Float> vector, String embeddingModel, int dimensions, String pipelineVersion) {
        this.chunkId = chunkId;
        this.embedding = vectorToString(vector);
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
        this.pipelineVersion = pipelineVersion != null ? pipelineVersion : "v1";
        this.createdAt = Instant.now();
    }

    public static String vectorToString(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(vector.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    public static List<Float> stringToVector(String str) {
        if (str == null || str.isBlank() || str.equals("[]")) {
            return List.of();
        }
        String clean = str.trim();
        if (clean.startsWith("[")) clean = clean.substring(1);
        if (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);
        String[] parts = clean.split(",");
        List<Float> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            list.add(Float.parseFloat(p.trim()));
        }
        return list;
    }

    public UUID getId() { return id; }
    public UUID getChunkId() { return chunkId; }
    public String getEmbedding() { return embedding; }
    public String getEmbeddingModel() { return embeddingModel; }
    public int getDimensions() { return dimensions; }
    public String getPipelineVersion() { return pipelineVersion; }
    public Instant getCreatedAt() { return createdAt; }

    public List<Float> getVector() {
        return stringToVector(this.embedding);
    }
}
