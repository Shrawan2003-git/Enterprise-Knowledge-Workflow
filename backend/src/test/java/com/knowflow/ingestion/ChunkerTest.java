package com.knowflow.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    private final Chunker chunker = new Chunker();

    @Test
    void chunksMultiPageTextWithOverlapAndHashes() {
        // Configure small budget for testing
        ReflectionTestUtils.setField(chunker, "targetTokenBudget", 20); // ~80 chars
        ReflectionTestUtils.setField(chunker, "overlapTokenBudget", 5);  // ~20 chars

        List<PageExtractionResult> pages = List.of(
                new PageExtractionResult(1, "First paragraph on page one with sufficient content to test chunking.\n\nSecond paragraph on page one."),
                new PageExtractionResult(2, "Third paragraph situated on page two.")
        );

        List<Chunker.RawChunk> chunks = chunker.chunkPages(pages);

        assertFalse(chunks.isEmpty());
        // Verify index ordering
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).chunkIndex());
            assertNotNull(chunks.get(i).contentHash());
            assertEquals(64, chunks.get(i).contentHash().length()); // SHA-256 hex
            assertTrue(chunks.get(i).pageStart() >= 1);
            assertTrue(chunks.get(i).pageEnd() <= 2);
            assertTrue(chunks.get(i).pageStart() <= chunks.get(i).pageEnd());
        }

        // Verify idempotency
        List<Chunker.RawChunk> rerun = chunker.chunkPages(pages);
        assertEquals(chunks.size(), rerun.size());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(chunks.get(i).contentHash(), rerun.get(i).contentHash());
            assertEquals(chunks.get(i).text(), rerun.get(i).text());
        }
    }
}
