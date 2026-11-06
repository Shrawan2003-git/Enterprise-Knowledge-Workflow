package com.knowflow.ingestion;

import com.knowflow.audit.AuditService;
import com.knowflow.copilot.ChunkEmbedding;
import com.knowflow.copilot.ChunkEmbeddingRepository;
import com.knowflow.copilot.EmbeddingService;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.documents.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class IngestionPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipelineRunner.class);

    private final DocumentVersionRepository versionRepository;
    private final DocumentRepository documentRepository;
    private final FileStorage fileStorage;
    private final ScanHook scanHook;
    private final PdfExtractor pdfExtractor;
    private final TextNormalizer textNormalizer;
    private final Chunker chunker;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final AuditService auditService;
    private final ErrorClassifier errorClassifier;
    private final boolean aiEnabled;

    public IngestionPipelineRunner(
            DocumentVersionRepository versionRepository,
            DocumentRepository documentRepository,
            FileStorage fileStorage,
            ScanHook scanHook,
            PdfExtractor pdfExtractor,
            TextNormalizer textNormalizer,
            Chunker chunker,
            DocumentChunkRepository chunkRepository,
            ChunkEmbeddingRepository chunkEmbeddingRepository,
            EmbeddingService embeddingService,
            AuditService auditService,
            ErrorClassifier errorClassifier,
            @Value("${knowflow.ai.enabled:false}") boolean aiEnabled
    ) {
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.fileStorage = fileStorage;
        this.scanHook = scanHook;
        this.pdfExtractor = pdfExtractor;
        this.textNormalizer = textNormalizer;
        this.chunker = chunker;
        this.chunkRepository = chunkRepository;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.embeddingService = embeddingService;
        this.auditService = auditService;
        this.errorClassifier = errorClassifier;
        this.aiEnabled = aiEnabled;
    }

    @Transactional
    public void execute(IngestionJob job) {
        log.info("Starting ingestion pipeline for job id={}, versionId={}, pipelineVersion={}",
                job.getId(), job.getVersionId(), job.getPipelineVersion());

        DocumentVersion version = versionRepository.findById(job.getVersionId())
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.FILE_NOT_FOUND, "Document version not found in database"));

        Document document = documentRepository.findById(version.getDocumentId())
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.FILE_NOT_FOUND, "Parent document not found in database"));

        version.setProcessingStatus("PROCESSING");
        versionRepository.save(version);

        try {
            // Step 1: Validate file presence
            if (!fileStorage.exists(version.getStorageKey())) {
                throw new IngestionException(IngestionErrorCode.FILE_NOT_FOUND, "File not present in physical storage: " + version.getStorageKey());
            }

            // Step 2: Security & Malware Scan
            try (InputStream scanStream = fileStorage.retrieve(version.getStorageKey())) {
                ScanHook.ScanResult scanResult = scanHook.scan(scanStream, document.getTitle());
                if (!scanResult.passed()) {
                    throw new IngestionException(IngestionErrorCode.SCAN_FAILED, "Malware scan failed: " + scanResult.detail());
                }
            }

            // Step 3: Page-aware Extraction
            List<PageExtractionResult> rawPages;
            try (InputStream extractStream = fileStorage.retrieve(version.getStorageKey())) {
                rawPages = pdfExtractor.extractPages(extractStream);
            }

            // Step 4: Text Normalization per page
            List<PageExtractionResult> normalizedPages = new ArrayList<>(rawPages.size());
            for (PageExtractionResult p : rawPages) {
                String normalizedText = textNormalizer.normalize(p.text());
                normalizedPages.add(new PageExtractionResult(p.pageNumber(), normalizedText));
            }

            // Step 5: Paragraph and Token-Aware Chunking
            List<Chunker.RawChunk> rawChunks = chunker.chunkPages(normalizedPages);
            log.info("Generated {} chunks for versionId={}", rawChunks.size(), version.getId());

            // Step 6: Idempotent Persistence (delete existing chunks & embeddings for version + pipelineVersion, then batch insert)
            List<DocumentChunk> existingChunks = chunkRepository.findByVersionIdAndPipelineVersionOrderByChunkIndexAsc(version.getId(), job.getPipelineVersion());
            if (!existingChunks.isEmpty()) {
                chunkEmbeddingRepository.deleteByChunkIdIn(existingChunks.stream().map(DocumentChunk::getId).toList());
            }
            chunkRepository.deleteByVersionIdAndPipelineVersion(version.getId(), job.getPipelineVersion());

            List<DocumentChunk> entities = new ArrayList<>(rawChunks.size());
            for (Chunker.RawChunk rc : rawChunks) {
                DocumentChunk entity = new DocumentChunk(
                        document.getId(),
                        version.getId(),
                        job.getPipelineVersion(),
                        rc.chunkIndex(),
                        rc.text(),
                        rc.pageStart(),
                        rc.pageEnd(),
                        rc.tokenCount(),
                        rc.contentHash()
                );
                entities.add(entity);
            }
            List<DocumentChunk> savedEntities = chunkRepository.saveAll(entities);

            // Step 7: Embeddings generation (if AI is enabled)
            if (aiEnabled && !savedEntities.isEmpty()) {
                log.info("Generating vector embeddings for {} chunks...", savedEntities.size());
                List<String> chunkTexts = savedEntities.stream().map(DocumentChunk::getText).toList();
                List<List<Float>> vectors = embeddingService.embedBatch(chunkTexts);
                List<ChunkEmbedding> embeddings = new ArrayList<>(savedEntities.size());
                for (int i = 0; i < savedEntities.size(); i++) {
                    DocumentChunk chunk = savedEntities.get(i);
                    List<Float> vector = vectors.get(i);
                    embeddings.add(new ChunkEmbedding(
                            chunk.getId(),
                            vector,
                            "text-embedding-3-small",
                            embeddingService.getDimensions(),
                            job.getPipelineVersion()
                    ));
                }
                chunkEmbeddingRepository.saveAll(embeddings);
                version.setProcessingStatus("READY");
            } else {
                version.setProcessingStatus(aiEnabled ? "READY" : "READY_KEYWORD_ONLY");
            }

            // Step 8: Finalize version status & atomic pipeline pointer
            version.setActivePipelineVersion(job.getPipelineVersion());
            version.setErrorCode(null);
            versionRepository.save(version);

            auditService.record(version.getUploadedBy(), "INGESTION_COMPLETED", "DOCUMENT_VERSION", version.getId(),
                    Map.of("chunksCount", entities.size(), "pipelineVersion", job.getPipelineVersion()));

            log.info("Successfully completed ingestion pipeline for versionId={}, generated {} chunks",
                    version.getId(), entities.size());

        } catch (Throwable t) {
            ErrorClassifier.ClassifiedError classified = errorClassifier.classify(t);
            version.setProcessingStatus("FAILED");
            version.setErrorCode(classified.code().name());
            versionRepository.save(version);
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
    }
}
