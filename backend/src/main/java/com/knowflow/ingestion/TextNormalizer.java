package com.knowflow.ingestion;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TextNormalizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");
    private static final Pattern LINE_WRAP_HYPHEN = Pattern.compile("(\\b\\w+)-\\r?\\n\\s*(\\w+\\b)");

    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 1. Remove non-printable control characters
        String cleaned = CONTROL_CHARS.matcher(text).replaceAll("");

        // 2. Unify line breaks to \n
        cleaned = cleaned.replace("\r\n", "\n").replace('\r', '\n');

        // 3. Fix words hyphenated across line breaks: e.g. "gov- \n ernance" -> "governance"
        cleaned = LINE_WRAP_HYPHEN.matcher(cleaned).replaceAll("$1$2");

        // 4. Compress horizontal whitespace
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");

        // 5. Cap consecutive vertical newlines to at most 2 (preserving distinct paragraphs)
        cleaned = MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n");

        return cleaned.trim();
    }
}
