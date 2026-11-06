package com.knowflow.copilot;

import java.util.UUID;

public record CopilotCitation(
        String sourceId,
        UUID documentId,
        String documentTitle,
        int versionNumber,
        Integer pageStart,
        Integer pageEnd,
        String pageHint,
        String snippet
) {
    public static String formatPageHint(Integer start, Integer end) {
        if (start == null && end == null) {
            return "page unavailable";
        }
        if (start != null && end != null && Objects.equals(start, end)) {
            return "p. " + start;
        }
        if (start != null && end != null) {
            return "pp. " + start + "-" + end;
        }
        if (start != null) {
            return "p. " + start;
        }
        return "p. " + end;
    }

    private static class Objects {
        static boolean equals(Object a, Object b) {
            return java.util.Objects.equals(a, b);
        }
    }
}
