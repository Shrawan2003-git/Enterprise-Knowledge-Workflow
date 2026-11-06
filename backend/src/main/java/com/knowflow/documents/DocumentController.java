package com.knowflow.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService, ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('KNOWLEDGE_MANAGER', 'ADMIN')")
    public ResponseEntity<DocumentDetailDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson
    ) throws IOException {
        CreateDocumentMetadata metadata = objectMapper.readValue(metadataJson, CreateDocumentMetadata.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadDocument(file, metadata));
    }

    @GetMapping
    public ResponseEntity<Page<DocumentSummaryDto>> listDocuments(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(documentService.listDocuments(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailDto> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentDetailDto> updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDocumentRequest request
    ) {
        return ResponseEntity.ok(documentService.updateMetadata(id, request));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VersionDto> uploadNewVersion(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeNote", required = false) String changeNote
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadNewVersion(id, file, changeNote));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<VersionDto>> listVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.listVersions(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer version
    ) {
        Resource fileResource = documentService.downloadDocument(id, version);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document.pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(fileResource);
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<Void> updatePermissions(
            @PathVariable UUID id,
            @RequestBody List<DocumentPermissionDto> permissions
    ) {
        documentService.updatePermissions(id, permissions);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archiveDocument(@PathVariable UUID id) {
        documentService.archiveDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreDocument(@PathVariable UUID id) {
        documentService.restoreDocument(id);
        return ResponseEntity.noContent().build();
    }
}
