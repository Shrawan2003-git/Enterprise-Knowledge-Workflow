package com.knowflow.copilot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    @Test
    @DisplayName("FakeEmbeddingService produces deterministic unit vectors with configured dimensions")
    void testFakeEmbeddingServiceDeterministic() {
        FakeEmbeddingService service = new FakeEmbeddingService(1536);

        assertEquals(1536, service.getDimensions());

        List<Float> vec1 = service.embed("Enterprise policies on vacation and sick leave");
        List<Float> vec2 = service.embed("Enterprise policies on vacation and sick leave");
        List<Float> vec3 = service.embed("Completely different topic: hardware procurement");

        assertNotNull(vec1);
        assertEquals(1536, vec1.size());
        assertEquals(1536, vec2.size());
        assertEquals(1536, vec3.size());

        // Identical texts must produce bit-for-bit identical vectors
        assertEquals(vec1, vec2);

        // Different texts must produce different vectors
        assertNotEquals(vec1, vec3);

        // Vector must be normalized to unit length: sum(v_i^2) ≈ 1.0
        double sumSq = 0.0;
        for (float f : vec1) {
            sumSq += f * f;
        }
        assertEquals(1.0, sumSq, 0.001);
    }

    @Test
    @DisplayName("Batch embedding produces consistent results with individual embedding")
    void testBatchEmbeddingMatchesIndividual() {
        FakeEmbeddingService service = new FakeEmbeddingService(1536);
        List<String> inputs = List.of("First chunk", "Second chunk", "Third chunk");

        List<List<Float>> batchResult = service.embedBatch(inputs);
        assertEquals(3, batchResult.size());

        for (int i = 0; i < inputs.size(); i++) {
            List<Float> individual = service.embed(inputs.get(i));
            assertEquals(individual, batchResult.get(i));
        }
    }

    @Test
    @DisplayName("ChunkEmbedding string serialization and deserialization roundtrips accurately")
    void testVectorSerializationRoundtrip() {
        List<Float> original = List.of(0.12345f, -0.6789f, 0.0f, 1.234f);
        String str = ChunkEmbedding.vectorToString(original);
        List<Float> roundtripped = ChunkEmbedding.stringToVector(str);

        assertEquals(original.size(), roundtripped.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i), roundtripped.get(i), 0.0001f);
        }
    }
}
