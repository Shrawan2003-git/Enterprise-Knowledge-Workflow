package com.knowflow.copilot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic fake embedding service for CI and testing.
 * Produces reproducible, normalized unit vectors of the requested dimension
 * using SHA-256 seeding without any external API calls.
 */
public class FakeEmbeddingService implements EmbeddingService {

    private final int dimensions;

    public FakeEmbeddingService() {
        this(1536);
    }

    public FakeEmbeddingService(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return generateZeroVector();
        }
        String[] tokens = text.toLowerCase().split("\\W+");
        float[] vector = new float[dimensions];
        int count = 0;
        for (String token : tokens) {
            if (token.isBlank()) continue;
            long seed = hashSeed(token);
            Random random = new Random(seed);
            for (int i = 0; i < dimensions; i++) {
                vector[i] += (float) random.nextGaussian();
            }
            count++;
        }
        if (count == 0) {
            return generateZeroVector();
        }

        double sumSq = 0.0;
        for (int i = 0; i < dimensions; i++) {
            sumSq += vector[i] * vector[i];
        }

        double norm = Math.sqrt(sumSq);
        if (norm == 0.0) {
            norm = 1.0;
        }

        List<Float> result = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            result.add((float) (vector[i] / norm));
        }
        return result;
    }

    private List<Float> generateZeroVector() {
        List<Float> zero = new ArrayList<>(dimensions);
        for (int i = 0; i < dimensions; i++) {
            zero.add(0.0f);
        }
        return zero;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null) {
            return List.of();
        }
        List<List<Float>> batch = new ArrayList<>(texts.size());
        for (String t : texts) {
            batch.add(embed(t));
        }
        return batch;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    private long hashSeed(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (digest[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            return text.hashCode();
        }
    }
}
