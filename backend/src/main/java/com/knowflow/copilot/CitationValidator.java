package com.knowflow.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates model-generated citations against the retrieved passages.
 * Drops hallucinated citation IDs, maps stable source tags [S#] to verified document metadata,
 * and downgrades ungrounded claims to INSUFFICIENT.
 */
public class CitationValidator {

    private static final Logger log = LoggerFactory.getLogger(CitationValidator.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[S(\\d+)\\]");

    public record ValidationResult(
            String sanitizedAnswer,
            List<CopilotCitation> validCitations,
            ConfidenceLevel confidence
    ) {}

    public static ValidationResult validate(
            String rawAnswer,
            List<String> rawCitationIds,
            ConfidenceLevel modelConfidence,
            List<RetrievedPassage> passages
    ) {
        if (passages == null || passages.isEmpty()) {
            return new ValidationResult(
                    rawAnswer != null && !rawAnswer.isBlank() ? rawAnswer : "No authorized evidence found to answer this question.",
                    List.of(),
                    ConfidenceLevel.INSUFFICIENT
            );
        }

        // Map [S1] -> Passage index 0, [S2] -> Passage index 1
        Map<String, RetrievedPassage> sourceMap = new LinkedHashMap<>();
        for (int i = 0; i < passages.size(); i++) {
            sourceMap.put("[S" + (i + 1) + "]", passages.get(i));
        }

        // Collect all citations mentioned either in rawCitationIds or extracted directly from answer text
        Set<String> referencedIds = new LinkedHashSet<>();
        if (rawCitationIds != null) {
            for (String cid : rawCitationIds) {
                if (cid != null) {
                    referencedIds.add(cid.trim());
                }
            }
        }

        if (rawAnswer != null) {
            Matcher m = CITATION_PATTERN.matcher(rawAnswer);
            while (m.find()) {
                referencedIds.add(m.group(0));
            }
        }

        List<CopilotCitation> verifiedCitations = new ArrayList<>();
        Set<String> invalidIds = new HashSet<>();

        for (String id : referencedIds) {
            if (sourceMap.containsKey(id)) {
                RetrievedPassage p = sourceMap.get(id);
                // Check if already added
                boolean alreadyAdded = verifiedCitations.stream().anyMatch(c -> c.sourceId().equals(id));
                if (!alreadyAdded) {
                    String snippet = p.text().length() > 200 ? p.text().substring(0, 197) + "..." : p.text();
                    verifiedCitations.add(new CopilotCitation(
                            id,
                            p.documentId(),
                            p.documentTitle(),
                            p.versionNumber(),
                            p.pageStart(),
                            p.pageEnd(),
                            CopilotCitation.formatPageHint(p.pageStart(), p.pageEnd()),
                            snippet
                    ));
                }
            } else {
                invalidIds.add(id);
                log.warn("Dropped hallucinated citation ID: {}", id);
            }
        }

        // Sanitize rawAnswer by removing invalid citation IDs
        String sanitizedAnswer = rawAnswer != null ? rawAnswer : "";
        for (String invalidId : invalidIds) {
            sanitizedAnswer = sanitizedAnswer.replace(invalidId, "");
        }
        sanitizedAnswer = sanitizedAnswer.trim();

        // Determine final confidence
        ConfidenceLevel finalConfidence = modelConfidence != null ? modelConfidence : ConfidenceLevel.ANSWERED;

        // If the answer is marked ANSWERED but has zero verified citations, downgrade to INSUFFICIENT
        if (finalConfidence == ConfidenceLevel.ANSWERED && verifiedCitations.isEmpty()) {
            log.info("Downgrading confidence to INSUFFICIENT because 0 valid citations were grounded");
            finalConfidence = ConfidenceLevel.INSUFFICIENT;
        }

        return new ValidationResult(sanitizedAnswer, verifiedCitations, finalConfidence);
    }
}
