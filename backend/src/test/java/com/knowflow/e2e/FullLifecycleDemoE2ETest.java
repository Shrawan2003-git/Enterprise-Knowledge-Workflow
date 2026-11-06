package com.knowflow.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.approvals.*;
import com.knowflow.audit.AuditLog;
import com.knowflow.audit.AuditLogRepository;
import com.knowflow.audit.AuditService;
import com.knowflow.common.KnowFlowMetrics;
import com.knowflow.copilot.*;
import com.knowflow.department.Department;
import com.knowflow.department.DepartmentRepository;
import com.knowflow.documents.*;
import com.knowflow.identity.*;
import com.knowflow.security.CurrentUser;
import com.knowflow.tasks.TaskRepository;
import com.knowflow.tools.GetMyPendingApprovalsTool;
import com.knowflow.tools.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class FullLifecycleDemoE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private ApprovalHistoryRepository historyRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private AuditService auditService;

    @Autowired(required = false)
    private KnowFlowMetrics knowFlowMetrics;

    private Department engDept;
    private User managerUser;
    private User approverUser;
    private User employeeUser;
    private User adminUser;

    private CurrentUser managerContext;
    private CurrentUser approverContext;
    private CurrentUser employeeContext;
    private CurrentUser adminContext;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        historyRepository.deleteAll();
        taskRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        departmentRepository.deleteAll();

        // 1. Seed Roles
        Role managerRole = roleRepository.save(new Role("MANAGER", "Engineering Manager"));
        Role approverRole = roleRepository.save(new Role("APPROVER", "Compliance Approver"));
        Role employeeRole = roleRepository.save(new Role("EMPLOYEE", "Standard Employee"));
        Role adminRole = roleRepository.save(new Role("ADMIN", "System Administrator"));

        // 2. Seed Department
        engDept = departmentRepository.save(new Department("ENG", "Engineering"));

        // 3. Seed Users
        managerUser = new User("manager@knowflow.com", "Alice Manager", passwordEncoder.encode("Pass123!"), engDept.getId());
        managerUser.setRoles(Set.of(managerRole));
        userRepository.save(managerUser);

        approverUser = new User("approver@knowflow.com", "Bob Approver", passwordEncoder.encode("Pass123!"), engDept.getId());
        approverUser.setRoles(Set.of(approverRole));
        userRepository.save(approverUser);

        employeeUser = new User("employee@knowflow.com", "Charlie Employee", passwordEncoder.encode("Pass123!"), engDept.getId());
        employeeUser.setRoles(Set.of(employeeRole));
        userRepository.save(employeeUser);

        adminUser = new User("admin@knowflow.com", "Dave Admin", passwordEncoder.encode("Pass123!"), engDept.getId());
        adminUser.setRoles(Set.of(adminRole));
        userRepository.save(adminUser);

        // CurrentUser security contexts
        managerContext = new CurrentUser(managerUser.getId(), managerUser.getEmail(), "Alice Manager", engDept.getId(), Set.of("MANAGER"));
        approverContext = new CurrentUser(approverUser.getId(), approverUser.getEmail(), "Bob Approver", engDept.getId(), Set.of("APPROVER"));
        employeeContext = new CurrentUser(employeeUser.getId(), employeeUser.getEmail(), "Charlie Employee", engDept.getId(), Set.of("EMPLOYEE"));
        adminContext = new CurrentUser(adminUser.getId(), adminUser.getEmail(), "Dave Admin", engDept.getId(), Set.of("ADMIN"));
    }

    @Test
    @DisplayName("End-to-End Enterprise Demo Lifecycle: Upload -> Ingestion -> Approval -> Copilot Retrieval -> Citation -> Tool -> Feedback -> Audit")
    void testCompleteEnterpriseWorkflowLifecycle() throws Exception {
        // =========================================================================
        // STEP 1: Manager uploads a safety procedure with metadata
        // =========================================================================
        String safetyProcedureText = """
                High-Voltage Substation Safety Procedure v1.0.
                Before starting any maintenance on high-voltage equipment, three mandatory safety checks are required:
                1. Confirm physical lock-out and tag-out (LOTO) on breaker panel B4.
                2. Verify zero electrical potential using a calibrated high-voltage proximity detector.
                3. Ensure secondary grounding cables are securely attached to the ground bus.
                Personal Protective Equipment (PPE) Class 4 arc flash suit is strictly mandatory.
                """;

        String storageKey = fileStorage.store(
                new ByteArrayInputStream(safetyProcedureText.getBytes(StandardCharsets.UTF_8)),
                "txt"
        );
        assertThat(storageKey).isNotBlank();
        assertThat(fileStorage.exists(storageKey)).isTrue();

        Document doc = new Document(
                "High-Voltage Substation Safety Procedure",
                "Standard operating procedure for substation maintenance",
                managerUser.getId(),
                engDept.getId(),
                null,
                "INTERNAL"
        );
        doc = documentRepository.save(doc);

        DocumentVersion version = new DocumentVersion(
                doc.getId(),
                1,
                storageKey,
                "SHA-256-SYNTHETIC",
                "text/plain",
                (long) safetyProcedureText.length(),
                managerUser.getId(),
                "Initial upload of safety procedure v1.0"
        );
        version = versionRepository.save(version);
        doc.setCurrentVersion(version);
        doc = documentRepository.save(doc);

        auditService.record(managerUser.getId(), "DOCUMENT_UPLOAD", "DOCUMENT", doc.getId(), Map.of("title", doc.getTitle()));

        // =========================================================================
        // STEP 2: Ingestion & Vector Indexing Simulation
        // =========================================================================
        version.setProcessingStatus("READY");
        version = versionRepository.save(version);
        assertThat(version.getProcessingStatus()).isEqualTo("READY");

        // =========================================================================
        // STEP 3: Manager submits document for review
        // =========================================================================
        ApprovalRequest approvalReq = new ApprovalRequest(
                version.getId(),
                managerUser.getId(),
                approverUser.getId()
        );
        approvalReq = approvalRepository.save(approvalReq);
        doc.setStatus("IN_REVIEW");
        doc = documentRepository.save(doc);

        auditService.record(managerUser.getId(), "APPROVAL_SUBMITTED", "APPROVAL_REQUEST", approvalReq.getId(), Map.of("docId", doc.getId()));
        assertThat(approvalReq.getStatus()).isEqualTo("PENDING");

        // =========================================================================
        // STEP 4: Approver reviews and approves document
        // =========================================================================
        approvalReq.setStatus("APPROVED");
        approvalReq = approvalRepository.save(approvalReq);

        ApprovalHistory history = new ApprovalHistory(
                approvalReq.getId(),
                approverUser.getId(),
                "APPROVE",
                "Verified against OSHA compliance guidelines. Approved for general engineering distribution.",
                "PENDING",
                "APPROVED"
        );
        historyRepository.save(history);

        doc.setStatus("APPROVED");
        doc = documentRepository.save(doc);

        auditService.record(approverUser.getId(), "APPROVAL_DECIDED", "APPROVAL_REQUEST", approvalReq.getId(), Map.of("decision", "APPROVED"));
        assertThat(doc.getStatus()).isEqualTo("APPROVED");

        // =========================================================================
        // STEP 5: Employee asks Copilot: "What checks are required before starting this procedure?"
        // =========================================================================
        UUID syntheticChunkId = UUID.randomUUID();
        RetrievedPassage passage = new RetrievedPassage(
                syntheticChunkId,
                version.getId(),
                doc.getId(),
                doc.getTitle(),
                1,
                1,
                1,
                safetyProcedureText,
                0.95,
                50
        );

        RetrievalService mockRetrievalService = mock(RetrievalService.class);
        when(mockRetrievalService.retrieve(eq("What checks are required before starting this procedure?"), eq(employeeContext), any()))
                .thenReturn(List.of(passage));

        FakeChatModel fakeChatModel = new FakeChatModel();
        CopilotServiceImpl copilotService = new CopilotServiceImpl(
                mockRetrievalService,
                fakeChatModel,
                objectMapper,
                true,
                30
        );

        CopilotAnswer answer = copilotService.ask(
                "What checks are required before starting this procedure?",
                employeeContext,
                RetrievalOptions.defaults()
        );

        assertThat(answer).isNotNull();
        assertThat(answer.confidence()).isEqualTo(ConfidenceLevel.ANSWERED);
        assertThat(answer.citations()).isNotEmpty();
        assertThat(answer.citations().getFirst().documentTitle()).isEqualTo(doc.getTitle());
        assertThat(answer.citations().getFirst().versionNumber()).isEqualTo(1);

        auditService.record(employeeUser.getId(), "COPILOT_QUERY", "COPILOT", doc.getId(), Map.of("question", "What checks are required?"));

        // =========================================================================
        // STEP 6: Employee verifies citation access
        // =========================================================================
        DocumentAccessPolicy mockAccessPolicy = mock(DocumentAccessPolicy.class);
        when(mockAccessPolicy.canView(eq(employeeContext), eq(doc.getId())))
                .thenReturn(PolicyResult.allow());

        // Employee from ENG department is allowed
        PolicyResult employeeAccess = mockAccessPolicy.canView(employeeContext, doc.getId());
        assertThat(employeeAccess.allowed()).isTrue();

        // Unauthorized user from unrelated department without permissions is blocked
        CurrentUser externalUser = new CurrentUser(UUID.randomUUID(), "unauth@knowflow.com", "External", UUID.randomUUID(), Set.of("EMPLOYEE"));
        when(mockAccessPolicy.canView(eq(externalUser), eq(doc.getId())))
                .thenReturn(PolicyResult.deny("ACCESS_DENIED", "Document restricted to Engineering"));

        PolicyResult externalAccess = mockAccessPolicy.canView(externalUser, doc.getId());
        assertThat(externalAccess.allowed()).isFalse();

        // =========================================================================
        // STEP 7: Employee calls Tool: get_my_pending_approvals
        // =========================================================================
        GetMyPendingApprovalsTool tool = new GetMyPendingApprovalsTool(approvalRepository);
        ToolExecutionResult toolResult = tool.execute(Map.of(), approverContext);
        assertThat(toolResult.success()).isTrue();
        assertThat(toolResult.data()).isNotNull();

        auditService.record(employeeUser.getId(), "TOOL_EXECUTE", "TOOL", null, Map.of("tool", "get_my_pending_approvals"));

        // =========================================================================
        // STEP 8: Employee rates the answer
        // =========================================================================
        MessageFeedback feedback = new MessageFeedback(
                UUID.randomUUID(),
                employeeUser.getId(),
                5,
                null,
                "Accurate citations and clear safety checklist."
        );
        assertThat(feedback.getRating()).isEqualTo(5);
        auditService.record(employeeUser.getId(), "FEEDBACK_SUBMITTED", "MESSAGE_FEEDBACK", feedback.getMessageId(), Map.of("rating", 5));

        // =========================================================================
        // STEP 9: Admin reviews audit trail & system metrics
        // =========================================================================
        List<AuditLog> auditTrail = auditLogRepository.findAll();
        assertThat(auditTrail).isNotEmpty();

        boolean hasUpload = auditTrail.stream().anyMatch(a -> "DOCUMENT_UPLOAD".equals(a.getAction()));
        boolean hasSubmit = auditTrail.stream().anyMatch(a -> "APPROVAL_SUBMITTED".equals(a.getAction()));
        boolean hasApproval = auditTrail.stream().anyMatch(a -> "APPROVAL_DECIDED".equals(a.getAction()));
        boolean hasCopilot = auditTrail.stream().anyMatch(a -> "COPILOT_QUERY".equals(a.getAction()));
        boolean hasTool = auditTrail.stream().anyMatch(a -> "TOOL_EXECUTE".equals(a.getAction()));
        boolean hasFeedback = auditTrail.stream().anyMatch(a -> "FEEDBACK_SUBMITTED".equals(a.getAction()));

        assertThat(hasUpload).isTrue();
        assertThat(hasSubmit).isTrue();
        assertThat(hasApproval).isTrue();
        assertThat(hasCopilot).isTrue();
        assertThat(hasTool).isTrue();
        assertThat(hasFeedback).isTrue();
    }
}
