package com.knowflow.copilot;

import java.util.List;

public interface EmbeddingService {

    /**
     * Compute embedding vector for a single text.
     */
    List<Float> embed(String text);

    /**
     * Compute embedding vectors for a batch of texts.
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * Expected dimensionality of vectors (e.g. 1536).
     */
    int getDimensions();
}
