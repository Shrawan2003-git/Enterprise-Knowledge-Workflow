package com.knowflow.tools;

import com.knowflow.copilot.RetrievalOptions;
import com.knowflow.copilot.RetrievalService;
import com.knowflow.copilot.RetrievedPassage;
import com.knowflow.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SearchAccessibleDocumentsTool implements ToolDefinition {

    private final RetrievalService retrievalService;

    public SearchAccessibleDocumentsTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public String getName() {
        return "search_accessible_documents";
    }

    @Override
    public String getDescription() {
        return "Searches across authorized enterprise documents using keywords, returning matching titles and snippets.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("query"),
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "The search query keywords"),
                        "limit", Map.of("type", "integer", "description", "Maximum documents to return (1-10)")
                )
        );
    }

    public record DocumentSearchResultItem(
            UUID documentId,
            String title,
            int versionNumber,
            String snippet,
            double relevanceScore
    ) {}

    @Override
    public ToolExecutionResult execute(Map<String, Object> rawArgs, CurrentUser user) {
        if (user == null) {
            return ToolExecutionResult.failure("User authentication is required");
        }

        if (rawArgs == null || !rawArgs.containsKey("query")) {
            return ToolExecutionResult.failure("Missing required argument: query");
        }

        String query = rawArgs.get("query").toString().trim();
        if (query.isBlank()) {
            return ToolExecutionResult.failure("Query string cannot be empty");
        }

        int limit = 5;
        if (rawArgs.containsKey("limit")) {
            try {
                int parsed = Integer.parseInt(rawArgs.get("limit").toString());
                limit = Math.clamp(parsed, 1, 10);
            } catch (Exception ignored) {}
        }

        List<RetrievedPassage> passages = retrievalService.retrieve(
                query,
                user,
                new RetrievalOptions(limit, 2000, 0.01)
        );

        List<DocumentSearchResultItem> results = passages.stream().map(p -> {
            String snippet = p.text().length() > 150 ? p.text().substring(0, 147) + "..." : p.text();
            return new DocumentSearchResultItem(
                    p.documentId(),
                    p.documentTitle(),
                    p.versionNumber(),
                    snippet,
                    p.score()
            );
        }).toList();

        return ToolExecutionResult.success(results);
    }
}
