package com.knowflow.approvals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.documents.*;
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

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowApprovalE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private ApprovalHistoryRepository historyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User kmUser;
    private User approverUser;
    private String kmToken;
    private String approverToken;

    @BeforeEach
    void setup() throws Exception {
        approvalRepository.deleteAll();
        historyRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role kmRole = roleRepository.save(new Role("KNOWLEDGE_MANAGER", "KM"));
        Role approverRole = roleRepository.save(new Role("APPROVER", "Approver"));

        kmUser = new User("km@flow.local", "KM User", passwordEncoder.encode("Pass123!"), null);
        kmUser.setRoles(Set.of(kmRole));
        userRepository.save(kmUser);

        approverUser = new User("approver@flow.local", "Approver User", passwordEncoder.encode("Pass123!"), null);
        approverUser.setRoles(Set.of(approverRole));
        userRepository.save(approverUser);

        kmToken = loginAndGetToken("km@flow.local", "Pass123!");
        approverToken = loginAndGetToken("approver@flow.local", "Pass123!");
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
    void endToEndReviewWorkflowSucceeds() throws Exception {
        // 1. KM uploads document
        byte[] pdf = "%PDF-1.4 Spec Content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", pdf);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Data Retention Policy", "Desc", null, null, "INTERNAL", null, null, "Initial");

        String uploadRes = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        DocumentDetailDto doc = objectMapper.readValue(uploadRes, DocumentDetailDto.class);

        // 2. Submit for review
        SubmitReviewRequest submitReq = new SubmitReviewRequest(doc.currentVersion().id(), approverUser.getId());
        String reviewRes = mockMvc.perform(post("/api/v1/documents/" + doc.id() + "/submit-review")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(submitReq))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        ApprovalRequestDto approval = objectMapper.readValue(reviewRes, ApprovalRequestDto.class);

        // 3. Document status should now reflect IN_REVIEW
        mockMvc.perform(get("/api/v1/documents/" + doc.id())
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // 4. Approver approves the version
        DecisionRequest approveReq = new DecisionRequest("Approved following security guidelines");
        mockMvc.perform(post("/api/v1/approvals/" + approval.id() + "/approve")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(approveReq))
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 5. Document status should now be APPROVED
        mockMvc.perform(get("/api/v1/documents/" + doc.id())
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 6. Submitter should have received an approval notification
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("APPROVAL_DECISION"));
    }

    @Test
    void submitterCannotSelfApprove() throws Exception {
        byte[] pdf = "%PDF-1.4 Spec Content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", pdf);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Self Approve Test", null, null, null, "INTERNAL", null, null, null);

        String uploadRes = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andReturn().getResponse().getContentAsString();

        DocumentDetailDto doc = objectMapper.readValue(uploadRes, DocumentDetailDto.class);

        // Assign self
        SubmitReviewRequest selfAssign = new SubmitReviewRequest(doc.currentVersion().id(), kmUser.getId());
        mockMvc.perform(post("/api/v1/documents/" + doc.id() + "/submit-review")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(selfAssign))
                        .header("Authorization", "Bearer " + kmToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Submitter cannot assign themselves as approver"));
    }

    @Test
    void rejectionRequiresJustificationComment() throws Exception {
        byte[] pdf = "%PDF-1.4 Spec Content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", pdf);
        CreateDocumentMetadata meta = new CreateDocumentMetadata("Reject Test", null, null, null, "INTERNAL", null, null, null);

        String uploadRes = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("metadata", objectMapper.writeValueAsString(meta))
                        .header("Authorization", "Bearer " + kmToken))
                .andReturn().getResponse().getContentAsString();

        DocumentDetailDto doc = objectMapper.readValue(uploadRes, DocumentDetailDto.class);

        SubmitReviewRequest submitReq = new SubmitReviewRequest(doc.currentVersion().id(), approverUser.getId());
        String reviewRes = mockMvc.perform(post("/api/v1/documents/" + doc.id() + "/submit-review")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(submitReq))
                        .header("Authorization", "Bearer " + kmToken))
                .andReturn().getResponse().getContentAsString();

        ApprovalRequestDto approval = objectMapper.readValue(reviewRes, ApprovalRequestDto.class);

        // Empty rejection comment should fail with 400 Bad Request
        DecisionRequest emptyReject = new DecisionRequest("   ");
        mockMvc.perform(post("/api/v1/approvals/" + approval.id() + "/reject")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emptyReject))
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A justification comment is required when rejecting a document review"));
    }
}
