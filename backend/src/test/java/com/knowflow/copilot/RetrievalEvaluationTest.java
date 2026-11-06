package com.knowflow.copilot;

import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.documents.DocumentVersion;
import com.knowflow.documents.DocumentVersionRepository;
import com.knowflow.documents.DocumentPermission;
import com.knowflow.documents.DocumentPermissionRepository;
import com.knowflow.identity.User;
import com.knowflow.identity.UserRepository;
import com.knowflow.ingestion.DocumentChunk;
import com.knowflow.ingestion.DocumentChunkRepository;
import com.knowflow.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RetrievalEvaluationTest {

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private ChunkEmbeddingRepository chunkEmbeddingRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private CopilotService copilotService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private CurrentUser generalEmployee;
    private CurrentUser privilegedUser;

    private UUID publicDocId;
    private UUID confidentialDocId;

    @BeforeEach
    void setUp() {
        cleanDb();

        UUID deptId = UUID.randomUUID();

        User u1 = userRepository.save(new User("emp@knowflow.internal", "General Employee", "hash", deptId));
        User u2 = userRepository.save(new User("director@knowflow.internal", "Director Approver", "hash", deptId));

        generalEmployee = new CurrentUser(u1.getId(), u1.getEmail(), u1.getDisplayName(), deptId, Set.of("ROLE_EMPLOYEE"));
        privilegedUser = new CurrentUser(u2.getId(), u2.getEmail(), u2.getDisplayName(), deptId, Set.of("ROLE_APPROVER"));

        // 1. Setup Public Approved Document
        Document pubDoc = new Document("Employee Handbook", "Standard policies", u2.getId(), deptId, null, "PUBLIC");
        pubDoc.setStatus("APPROVED");
        pubDoc = documentRepository.save(pubDoc);
        publicDocId = pubDoc.getId();

        DocumentVersion pubVer = new DocumentVersion(publicDocId, 1, "key-pub", "checksum1", "application/pdf", 1024L, u2.getId(), "initial upload");
        pubVer.setProcessingStatus("READY");
        pubVer = versionRepository.save(pubVer);

        pubDoc.setCurrentVersion(pubVer);
        pubDoc = documentRepository.save(pubDoc);

        DocumentChunk pubChunk = new DocumentChunk(publicDocId, pubVer.getId(), "v1", 0, "The company annual vacation policy grants 20 days paid leave.", 3, 3, 12, "hash1");
        pubChunk = chunkRepository.save(pubChunk);
        List<Float> pubVec = embeddingService.embed(pubChunk.getText());
        chunkEmbeddingRepository.save(new ChunkEmbedding(pubChunk.getId(), pubVec, "text-embedding-3-small", 1536, "v1"));

        // 2. Setup Confidential Document (M&A)
        Document confDoc = new Document("Project Titan Confidential M&A", "Secret Acquisition", privilegedUser.id(), deptId, null, "CONFIDENTIAL");
        confDoc.setStatus("APPROVED");
        confDoc = documentRepository.save(confDoc);
        confidentialDocId = confDoc.getId();

        DocumentVersion confVer = new DocumentVersion(confidentialDocId, 1, "key-titan", "checksum2", "application/pdf", 2048L, privilegedUser.id(), "confidential v1");
        confVer.setProcessingStatus("READY");
        confVer = versionRepository.save(confVer);

        confDoc.setCurrentVersion(confVer);
        confDoc = documentRepository.save(confDoc);

        DocumentChunk confChunk = new DocumentChunk(confidentialDocId, confVer.getId(), "v1", 0, "Project Titan acquisition confidential details and bidder name Apex Global.", 1, 1, 14, "hash2");
        confChunk = chunkRepository.save(confChunk);
        List<Float> confVec = embeddingService.embed(confChunk.getText());
        chunkEmbeddingRepository.save(new ChunkEmbedding(confChunk.getId(), confVec, "text-embedding-3-small", 1536, "v1"));
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void cleanDb() {
        chunkEmbeddingRepository.deleteAll();
        chunkRepository.deleteAll();
        permissionRepository.deleteAll();
        documentRepository.deleteAll();
        versionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("SQL-level ACL prevents general employee from retrieving confidential chunks (Leakage = 0)")
    void testZeroLeakageOnPermissionTrap() {
        List<RetrievedPassage> passages = retrievalService.retrieve(
                "Who is the bidder in Project Titan acquisition?",
                generalEmployee,
                RetrievalOptions.defaults()
        );

        // Leakage MUST BE 0.0: No chunks from confidentialDocId should ever be returned to general employee
        boolean leaked = passages.stream().anyMatch(p -> p.documentId().equals(confidentialDocId));
        assertFalse(leaked, "CRITICAL: General employee retrieved unauthorized confidential chunk!");
    }

    @Test
    @DisplayName("Authorized user successfully retrieves confidential passages")
    void testAuthorizedUserRetrievesConfidentialPassages() {
        List<RetrievedPassage> passages = retrievalService.retrieve(
                "Who is the bidder in Project Titan acquisition?",
                privilegedUser,
                RetrievalOptions.defaults()
        );

        assertFalse(passages.isEmpty(), "Privileged user should retrieve the passage");
        assertEquals(confidentialDocId, passages.getFirst().documentId());
        assertTrue(passages.getFirst().text().contains("Apex Global"));
    }

    @Test
    @DisplayName("Revoking permission immediately excludes document on the very next retrieval")
    void testPermissionRevocationExcludesImmediately() {
        DocumentPermission perm = new DocumentPermission(confidentialDocId, "USER", generalEmployee.id(), "VIEW");
        perm = permissionRepository.save(perm);

        List<RetrievedPassage> beforeRevoke = retrievalService.retrieve(
                "Project Titan acquisition",
                generalEmployee,
                RetrievalOptions.defaults()
        );
        assertTrue(beforeRevoke.stream().anyMatch(p -> p.documentId().equals(confidentialDocId)));

        permissionRepository.delete(perm);

        List<RetrievedPassage> afterRevoke = retrievalService.retrieve(
                "Project Titan acquisition",
                generalEmployee,
                RetrievalOptions.defaults()
        );
        assertFalse(afterRevoke.stream().anyMatch(p -> p.documentId().equals(confidentialDocId)),
                "Revoked document must be immediately excluded from retrieval");
    }

    @Test
    @DisplayName("Archived document is excluded from hybrid retrieval per policy")
    void testArchivedDocumentExcludedFromRetrieval() {
        Document pubDoc = documentRepository.findById(publicDocId).orElseThrow();
        pubDoc.setStatus("ARCHIVED");
        documentRepository.save(pubDoc);

        List<RetrievedPassage> passages = retrievalService.retrieve(
                "What is the annual vacation policy?",
                generalEmployee,
                RetrievalOptions.defaults()
        );

        assertFalse(passages.stream().anyMatch(p -> p.documentId().equals(publicDocId)),
                "Archived documents must never appear in hybrid retrieval results");
    }

    @Test
    @DisplayName("Evaluation suite: Execute dataset.jsonl cases, verify thresholds, export eval_report.csv")
    void testEvaluationSuiteAgainstThresholds() throws Exception {
        java.io.InputStream is = getClass().getResourceAsStream("/eval/dataset.jsonl");
        assertNotNull(is, "eval/dataset.jsonl must exist");

        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
        String line;
        int totalCases = 0;
        int permissionTraps = 0;
        int leakedTraps = 0;
        int abstentionExpected = 0;
        int abstentionAccurate = 0;
        int answeredCases = 0;
        int groundedAnswers = 0;

        java.io.File csvFile = new java.io.File("target/eval_report.csv");
        csvFile.getParentFile().mkdirs();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(csvFile, java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("id,question,expectAbstain,permissionTrap,retrievedDocsCount,confidence,citationsCount,passed");

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(line);
                String id = node.get("id").asText();
                String question = node.get("question").asText();
                boolean expectAbstain = node.get("expectAbstain").asBoolean();
                boolean isTrap = node.get("permissionTrap").asBoolean();
                totalCases++;

                // Query as general employee
                CopilotAnswer answer = copilotService.ask(question, generalEmployee, RetrievalOptions.defaults());

                boolean passed = true;
                if (isTrap) {
                    permissionTraps++;
                    // General employee must never see confidential chunks
                    boolean leaked = answer.citations().stream().anyMatch(c -> c.documentId().equals(confidentialDocId));
                    if (leaked) {
                        leakedTraps++;
                        passed = false;
                    }
                    if (answer.confidence() != ConfidenceLevel.INSUFFICIENT) {
                        passed = false;
                    }
                } else if (expectAbstain) {
                    abstentionExpected++;
                    if (answer.confidence() == ConfidenceLevel.INSUFFICIENT) {
                        abstentionAccurate++;
                    } else {
                        passed = false;
                    }
                } else {
                    answeredCases++;
                    // Valid answers must have verified citations
                    if (answer.confidence() == ConfidenceLevel.ANSWERED && !answer.citations().isEmpty()) {
                        groundedAnswers++;
                    }
                }

                pw.printf("%s,\"%s\",%b,%b,%d,%s,%d,%b%n",
                        id, question.replace("\"", "\"\""), expectAbstain, isTrap,
                        answer.citations().size(), answer.confidence(), answer.citations().size(), passed);
            }
        }

        double leakage = permissionTraps > 0 ? (double) leakedTraps / permissionTraps : 0.0;
        double abstentionAccuracy = abstentionExpected > 0 ? (double) abstentionAccurate / abstentionExpected : 1.0;

        System.out.println("==================================================================");
        System.out.println("                   KNOWFLOW COPILOT EVALUATION                     ");
        System.out.println("==================================================================");
        System.out.printf("Total Cases Evaluated:       %d%n", totalCases);
        System.out.printf("Permission Trap Cases:       %d (Violations: %d, Leakage: %.2f%%)%n", permissionTraps, leakedTraps, leakage * 100);
        System.out.printf("Abstention Test Cases:       %d (Accurate: %d, Accuracy: %.2f%%)%n", abstentionExpected, abstentionAccurate, abstentionAccuracy * 100);
        System.out.println("Manual-review CSV report generated at: target/eval_report.csv");
        System.out.println("==================================================================");

        assertEquals(0.0, leakage, "CRITICAL: Authorization leakage must be exactly 0.0");
        assertTrue(abstentionAccuracy >= 0.90, "Abstention accuracy must meet threshold (>= 90%)");
    }
}
