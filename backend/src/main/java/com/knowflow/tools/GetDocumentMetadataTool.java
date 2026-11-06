package com.knowflow.tools;

import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentAccessPolicy;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GetDocumentMetadataTool implements ToolDefinition {

    private final DocumentRepository documentRepository;
    private final DocumentAccessPolicy accessPolicy;

    public GetDocumentMetadataTool(DocumentRepository documentRepository, DocumentAccessPolicy accessPolicy) {
        this.documentRepository = documentRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public String getName() {
        return "get_document_metadata";
    }

    @Override
    public String getDescription() {
        return "Retrieves document metadata (title, classification, status) if accessible to the current user.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("documentId"),
                "properties", Map.of(
                        "documentId", Map.of("type", "string", "format", "uuid", "description", "The UUID of the document")
                )
        );
    }

    public record DocumentMetadataItem(
            UUID documentId,
            String title,
            String description,
            String classification,
            String status,
            Instant updatedAt
    ) {}

    @Override
    public ToolExecutionResult execute(Map<String, Object> rawArgs, CurrentUser user) {
        if (user == null) {
            return ToolExecutionResult.failure("User authentication is required");
        }

        if (rawArgs == null || !rawArgs.containsKey("documentId")) {
            return ToolExecutionResult.failure("Missing required argument: documentId");
        }

        UUID documentId;
        try {
            documentId = UUID.fromString(rawArgs.get("documentId").toString().trim());
        } catch (Exception e) {
            return ToolExecutionResult.failure("Malformed argument: documentId must be a valid UUID");
        }

        // Strict authorization check via central DocumentAccessPolicy
        if (!accessPolicy.canView(user, documentId).allowed()) {
            return ToolExecutionResult.failure("ACCESS_DENIED: You are not authorized to view document " + documentId);
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return ToolExecutionResult.failure("Document not found: " + documentId);
        }

        return ToolExecutionResult.success(new DocumentMetadataItem(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.getClassification(),
                document.getStatus(),
                document.getUpdatedAt()
        ));
    }
}
