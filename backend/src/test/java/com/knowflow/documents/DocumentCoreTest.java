package com.knowflow.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentCoreTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String kmToken;
    private String employeeToken;
    private User adminUser;
    private User employeeUser;

    @BeforeEach
    void setup() throws Exception {
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = roleRepository.save(new Role("ADMIN", "Admin"));
        Role kmRole = roleRepository.save(new Role("KNOWLEDGE_MANAGER", "KM"));
        Role employeeRole = roleRepository.save(new Role("EMPLOYEE", "Employee"));

        adminUser = new User("admin@test.com", "Admin", passwordEncoder.encode("Pass123!"), null);
        adminUser.setRoles(Set.of(adminRole));
        userRepository.save(adminUser);

        User kmUser = new User("km@test.com", "KM", passwordEncoder.encode("Pass123!"), null);
        kmUser.setRoles(Set.of(kmRole));
        userRepository.save(kmUser);

        employeeUser = new User("emp@test.com", "Employee", passwordEncoder.encode("Pass123!"), null);
        employeeUser.setRoles(Set.of(employeeRole));
        userRepository.save(employeeUser);

        adminToken = loginAndGetToken("admin@test.com", "Pass123!");
        kmToken = loginAndGetToken("km@test.com", "Pass123!");
        employeeToken = loginAndGetToken("emp@test.com", "Pass123!");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        String res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AuthResponse auth = objectMapper.readValue(res, AuthResponse.class);
        return auth.accessToken();
    }

    @Test
    void uploadPdfSucceedsForKnowledgeManager() throws Exception {
        byte[] validPdf = "%PDF-1.4 sample content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "safety.pdf", "application/pdf", validPdf);

        CreateDocumentMetadata meta = new CreateDocumentMetadata(
                "Plant Safety Standard", "Operations manual", null, null, "INTERNAL", Set.of("safety"), null, "Initial"
        );

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Plant Safety Standard"))
                .andExpect(jsonPath("$.currentVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.currentVersion.checksum").isNotEmpty());
    }

    @Test
    void uploadDisguisedExeRejectedByMagicBytesSniffing() throws Exception {
        byte[] fakePdf = "MZ exe binary masquerading as pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", fakePdf);

        CreateDocumentMetadata meta = new CreateDocumentMetadata("Bad Doc", null, null, null, "INTERNAL", null, null, null);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded file signature does not match PDF format"));
    }

    @Test
    void employeeCannotUploadDocumentsDirectly() throws Exception {
        byte[] validPdf = "%PDF-1.4 test".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", validPdf);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Title", null, null, null, "INTERNAL", null, null, null);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void confidentialDocumentReturns404ForUnauthorizedEmployee() throws Exception {
        // KM uploads confidential document
        byte[] validPdf = "%PDF-1.4 confidential data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "secret.pdf", "application/pdf", validPdf);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Financial Audit", null, null, null, "CONFIDENTIAL", null, null, null);

        String res = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        DocumentDetailDto created = objectMapper.readValue(res, DocumentDetailDto.class);

        // Employee attempting to read returns 404 existence hiding
        mockMvc.perform(get("/api/v1/documents/" + created.id())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNotFound());

        // Explicit permission granted to Employee
        DocumentPermissionDto perm = new DocumentPermissionDto("USER", employeeUser.getId(), "VIEW");
        mockMvc.perform(put("/api/v1/documents/" + created.id() + "/permissions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(List.of(perm)))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isNoContent());

        // Employee can now view
        mockMvc.perform(get("/api/v1/documents/" + created.id())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Financial Audit"));
    }

    @Test
    void uploadNewVersionIncrementsVersionNumber() throws Exception {
        byte[] v1 = "%PDF-1.4 Version 1".getBytes();
        MockMultipartFile file1 = new MockMultipartFile("file", "spec.pdf", "application/pdf", v1);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Engineering Spec", null, null, null, "PUBLIC", null, null, null);

        String res = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file1)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        DocumentDetailDto created = objectMapper.readValue(res, DocumentDetailDto.class);

        // Upload version 2
        byte[] v2 = "%PDF-1.4 Version 2".getBytes();
        MockMultipartFile file2 = new MockMultipartFile("file", "spec_v2.pdf", "application/pdf", v2);
        mockMvc.perform(multipart("/api/v1/documents/" + created.id() + "/versions")
                        .file(file2)
                        .param("changeNote", "Updated revisions")
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        // List versions returns 2 versions
        mockMvc.perform(get("/api/v1/documents/" + created.id() + "/versions")
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void downloadAuthorizedVersionReturnsSafeHeaders() throws Exception {
        byte[] content = "%PDF-1.4 Document file stream".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", content);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Public Manual", null, null, null, "PUBLIC", null, null, null);

        String res = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        DocumentDetailDto created = objectMapper.readValue(res, DocumentDetailDto.class);

        mockMvc.perform(get("/api/v1/documents/" + created.id() + "/download")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(content));
    }

    @Test
    void optimisticLockingReturnsConflictOnStaleVersion() throws Exception {
        byte[] v1 = "%PDF-1.4 Sample".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", v1);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Original", null, null, null, "PUBLIC", null, null, null);

        String res = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andReturn().getResponse().getContentAsString();
        DocumentDetailDto created = objectMapper.readValue(res, DocumentDetailDto.class);

        // Update with wrong version (e.g., 99 instead of 0)
        UpdateDocumentRequest badUpdate = new UpdateDocumentRequest("Conflict", null, null, null, null, null, 99);
        mockMvc.perform(put("/api/v1/documents/" + created.id())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(badUpdate))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
