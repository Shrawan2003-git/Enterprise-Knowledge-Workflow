package com.knowflow.search;

import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentSummaryDto;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.ingestion.DocumentChunk;
import com.knowflow.ingestion.DocumentChunkRepository;
import com.knowflow.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KeywordSearchService implements SearchService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;

    public KeywordSearchService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    public Page<DocumentSearchResult> search(
            String query,
            UUID categoryId,
            String tag,
            UUID departmentId,
            String status,
            UUID ownerId,
            String classification,
            Instant fromDate,
            Instant toDate,
            CurrentUser user,
            Pageable pageable
    ) {
        if (user == null) {
            return Page.empty(pageable);
        }

        // Retrieve candidates from repository
        List<Document> allDocs = documentRepository.findAll();

        // 1. Critical Server-Side Access Control (ACL) Filtering before counts or snippets
        List<Document> accessibleDocs = allDocs.stream()
                .filter(doc -> isAuthorized(user, doc))
                .toList();

        // 2. Multi-filter criteria
        List<DocumentSearchResult> filtered = accessibleDocs.stream()
                .filter(doc -> {
                    if (status != null && !status.equalsIgnoreCase(doc.getStatus())) return false;
                    if ("ARCHIVED".equalsIgnoreCase(doc.getStatus()) && (status == null || !"ARCHIVED".equalsIgnoreCase(status))) return false;
                    if (categoryId != null && (doc.getCategory() == null || !categoryId.equals(doc.getCategory().getId()))) return false;
                    if (departmentId != null && !departmentId.equals(doc.getDepartmentId())) return false;
                    if (ownerId != null && !ownerId.equals(doc.getOwnerId())) return false;
                    if (classification != null && !classification.equalsIgnoreCase(doc.getClassification())) return false;
                    if (fromDate != null && doc.getCreatedAt().isBefore(fromDate)) return false;
                    if (toDate != null && doc.getCreatedAt().isAfter(toDate)) return false;
                    if (tag != null && (doc.getTags() == null || doc.getTags().stream().noneMatch(t -> t.getName().equalsIgnoreCase(tag.trim())))) return false;

                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.toLowerCase().trim();
                        boolean matchesTitle = doc.getTitle() != null && doc.getTitle().toLowerCase().contains(q);
                        boolean matchesDesc = doc.getDescription() != null && doc.getDescription().toLowerCase().contains(q);
                        return matchesTitle || matchesDesc;
                    }

                    return true;
                })
                .map(doc -> {
                    String snippet = generateSafeSnippet(doc, query);
                    double score = calculateRelevance(doc, query);
                    return new DocumentSearchResult(DocumentSummaryDto.fromEntity(doc), snippet, score);
                })
                .sorted(Comparator.comparingDouble(DocumentSearchResult::relevanceScore).reversed())
                .toList();

        // 3. Paginate
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<DocumentSearchResult> pageContent = (start <= end && start < filtered.size()) ? filtered.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    private boolean isAuthorized(CurrentUser user, Document doc) {
        if (user.isAdmin()) return true;
        if (user.id().equals(doc.getOwnerId())) return true;
        if ("ARCHIVED".equalsIgnoreCase(doc.getStatus())) return false;

        String classification = doc.getClassification() != null ? doc.getClassification().toUpperCase() : "INTERNAL";
        if ("PUBLIC".equals(classification)) return true;
        if ("INTERNAL".equals(classification)) {
            return doc.getDepartmentId() != null && doc.getDepartmentId().equals(user.departmentId());
        }

        // CONFIDENTIAL / RESTRICTED require explicit grants (evaluated via document permissions in DocumentAccessPolicy)
        return false;
    }

    private String generateSafeSnippet(Document doc, String query) {
        if (query == null || query.trim().isEmpty()) {
            return doc.getDescription() != null && doc.getDescription().length() > 120
                    ? doc.getDescription().substring(0, 120) + "..."
                    : doc.getDescription();
        }

        String q = query.toLowerCase().trim();
        String desc = doc.getDescription() != null ? doc.getDescription() : "";
        int idx = desc.toLowerCase().indexOf(q);

        if (idx != -1) {
            int start = Math.max(0, idx - 40);
            int end = Math.min(desc.length(), idx + q.length() + 60);
            String snippet = desc.substring(start, end);
            return (start > 0 ? "..." : "") + snippet + (end < desc.length() ? "..." : "");
        }

        return doc.getTitle();
    }

    private double calculateRelevance(Document doc, String query) {
        if (query == null || query.trim().isEmpty()) return 1.0;
        String q = query.toLowerCase().trim();
        double score = 0.0;
        if (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(q)) score += 5.0;
        if (doc.getDescription() != null && doc.getDescription().toLowerCase().contains(q)) score += 2.0;
        return score;
    }

    @Override
    public List<ChunkSearchResult> searchChunks(String query, CurrentUser user, int limit) {
        if (query == null || query.isBlank() || user == null) {
            return List.of();
        }

        // 1. Fetch accessible documents
        List<Document> allDocs = documentRepository.findAll();
        List<Document> accessibleDocs = allDocs.stream()
                .filter(doc -> isAuthorized(user, doc))
                .filter(doc -> user.isAdmin() || user.roles().contains("KNOWLEDGE_MANAGER") || "APPROVED".equalsIgnoreCase(doc.getStatus()) || user.id().equals(doc.getOwnerId()))
                .toList();

        if (accessibleDocs.isEmpty()) {
            return List.of();
        }

        Map<UUID, Document> docMap = accessibleDocs.stream()
                .collect(Collectors.toMap(Document::getId, d -> d));

        List<UUID> versionIds = accessibleDocs.stream()
                .map(Document::getCurrentVersionId)
                .filter(Objects::nonNull)
                .toList();

        if (versionIds.isEmpty()) {
            return List.of();
        }

        List<DocumentVersion> readyVersions = versionRepository.findAllById(versionIds).stream()
                .filter(v -> "READY".equalsIgnoreCase(v.getProcessingStatus()))
                .toList();

        Map<UUID, DocumentVersion> versionMap = readyVersions.stream()
                .collect(Collectors.toMap(DocumentVersion::getId, v -> v));

        List<UUID> readyVersionIds = readyVersions.stream().map(DocumentVersion::getId).toList();
        if (readyVersionIds.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> matchingChunks = chunkRepository.searchInVersions(query.trim(), readyVersionIds);

        int maxResults = Math.min(limit > 0 ? limit : 10, 50);

        return matchingChunks.stream()
                .filter(c -> {
                    DocumentVersion v = versionMap.get(c.getVersionId());
                    return v != null && c.getPipelineVersion().equals(v.getActivePipelineVersion());
                })
                .map(c -> {
                    Document doc = docMap.get(c.getDocumentId());
                    DocumentVersion v = versionMap.get(c.getVersionId());
                    String snippet = generateChunkSnippet(c.getText(), query);
                    double score = calculateChunkRelevance(c.getText(), query);
                    return new ChunkSearchResult(
                            c.getId(),
                            c.getDocumentId(),
                            doc != null ? doc.getTitle() : "Untitled Document",
                            c.getVersionId(),
                            v != null ? v.getVersionNumber() : 1,
                            c.getPageStart(),
                            c.getPageEnd(),
                            snippet,
                            score
                    );
                })
                .sorted(Comparator.comparingDouble(ChunkSearchResult::relevanceScore).reversed())
                .limit(maxResults)
                .toList();
    }

    private String generateChunkSnippet(String text, String query) {
        if (text == null || text.isBlank()) return "";
        if (query == null || query.isBlank()) {
            return text.length() > 200 ? text.substring(0, 200) + "..." : text;
        }

        String q = query.toLowerCase().trim();
        int idx = text.toLowerCase().indexOf(q);
        if (idx != -1) {
            int start = Math.max(0, idx - 60);
            int end = Math.min(text.length(), idx + q.length() + 80);
            return (start > 0 ? "..." : "") + text.substring(start, end).trim() + (end < text.length() ? "..." : "");
        }

        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private double calculateChunkRelevance(String text, String query) {
        if (text == null || query == null) return 1.0;
        String q = query.toLowerCase().trim();
        int occurrences = 0;
        int idx = 0;
        String lower = text.toLowerCase();
        while ((idx = lower.indexOf(q, idx)) != -1) {
            occurrences++;
            idx += q.length();
        }
        return 1.0 + (occurrences * 2.5);
    }
}
