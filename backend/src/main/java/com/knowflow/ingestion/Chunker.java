package com.knowflow.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class Chunker {

    private static final int CHARS_PER_TOKEN = 4;

    @Value("${knowflow.ingestion.chunk-token-budget:500}")
    private int targetTokenBudget;

    @Value("${knowflow.ingestion.chunk-overlap-tokens:50}")
    private int overlapTokenBudget;

    public record RawChunk(
            int chunkIndex,
            String text,
            int pageStart,
            int pageEnd,
            int tokenCount,
            String contentHash
    ) {}

    public List<RawChunk> chunkPages(List<PageExtractionResult> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        int maxChars = targetTokenBudget * CHARS_PER_TOKEN;
        int overlapChars = overlapTokenBudget * CHARS_PER_TOKEN;

        List<RawChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        StringBuilder currentChunkText = new StringBuilder();
        int currentStartPage = -1;
        int currentEndPage = -1;

        for (PageExtractionResult page : pages) {
            String pageText = page.text() != null ? page.text().trim() : "";
            if (pageText.isBlank()) {
                continue;
            }

            // Split page into paragraphs
            String[] paragraphs = pageText.split("\n\n");

            for (String para : paragraphs) {
                String trimmedPara = para.trim();
                if (trimmedPara.isEmpty()) continue;

                if (currentStartPage == -1) {
                    currentStartPage = page.pageNumber();
                }
                currentEndPage = page.pageNumber();

                // If adding this paragraph exceeds maxChars and we already have content
                if (currentChunkText.length() > 0 && (currentChunkText.length() + trimmedPara.length() + 2) > maxChars) {
                    // Finalize current chunk
                    String fullText = currentChunkText.toString().trim();
                    chunks.add(buildRawChunk(chunkIndex++, fullText, currentStartPage, currentEndPage));

                    // Build overlap from trailing text of current chunk
                    String overlap = computeOverlap(fullText, overlapChars);
                    currentChunkText.setLength(0);
                    if (!overlap.isEmpty()) {
                        currentChunkText.append(overlap).append("\n\n");
                    }
                    currentStartPage = page.pageNumber();
                }

                if (currentChunkText.length() > 0) {
                    currentChunkText.append("\n\n");
                }
                currentChunkText.append(trimmedPara);
            }
        }

        // Final chunk
        if (currentChunkText.length() > 0) {
            String remaining = currentChunkText.toString().trim();
            if (!remaining.isEmpty()) {
                chunks.add(buildRawChunk(chunkIndex, remaining, currentStartPage != -1 ? currentStartPage : 1, currentEndPage != -1 ? currentEndPage : 1));
            }
        }

        return chunks;
    }

    private String computeOverlap(String text, int overlapChars) {
        if (text == null || text.length() <= overlapChars) {
            return "";
        }
        int splitPoint = text.length() - overlapChars;
        // Adjust to next space or newline boundary if possible
        int nextSpace = text.indexOf(' ', splitPoint);
        if (nextSpace != -1 && nextSpace < text.length()) {
            splitPoint = nextSpace + 1;
        }
        return text.substring(splitPoint).trim();
    }

    private RawChunk buildRawChunk(int index, String text, int startPage, int endPage) {
        int tokenCount = Math.max(1, text.length() / CHARS_PER_TOKEN);
        String hash = computeSha256(text);
        return new RawChunk(index, text, startPage, endPage, tokenCount, hash);
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
