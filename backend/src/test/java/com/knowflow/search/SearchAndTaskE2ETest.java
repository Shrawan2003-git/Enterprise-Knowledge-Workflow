package com.knowflow.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.documents.Document;
import com.knowflow.documents.DocumentRepository;
import com.knowflow.identity.Role;
import com.knowflow.identity.RoleRepository;
import com.knowflow.identity.User;
import com.knowflow.identity.UserRepository;
import com.knowflow.notifications.Notification;
import com.knowflow.notifications.NotificationRepository;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.JwtProvider;
import com.knowflow.tasks.CreateTaskRequest;
import com.knowflow.tasks.TaskDto;
import com.knowflow.tasks.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchAndTaskE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private com.knowflow.tasks.TaskCommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User employeeUser;
    private String employeeToken;

    @BeforeEach
    void setup() {
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        notificationRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role empRole = roleRepository.save(new Role("EMPLOYEE", "Employee"));

        employeeUser = new User("emp.search@knowflow.internal", "Search Employee", "dummyHash", null);
        employeeUser.setRoles(Set.of(empRole));
        employeeUser = userRepository.save(employeeUser);

        CurrentUser currentUser = new CurrentUser(
                employeeUser.getId(),
                employeeUser.getEmail(),
                employeeUser.getDisplayName(),
                employeeUser.getDepartmentId(),
                Set.of("EMPLOYEE")
        );
        employeeToken = jwtProvider.generateAccessToken(currentUser);
    }

    @Test
    void searchEndpointReturnsAccessibleDocuments() throws Exception {
        Document doc = new Document(
                "IT Security Governance Policy",
                "Internal operating guidelines for enterprise information security.",
                employeeUser.getId(),
                null,
                null,
                "INTERNAL"
        );
        doc.setStatus("APPROVED");
        documentRepository.save(doc);

        mockMvc.perform(get("/api/v1/search")
                        .header("Authorization", "Bearer " + employeeToken)
                        .param("q", "Security")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].document.title").value("IT Security Governance Policy"))
                .andExpect(jsonPath("$.content[0].document.classification").value("INTERNAL"));
    }

    @Test
    void taskLifecycleEndToEnd() throws Exception {
        // 1. Create a task
        CreateTaskRequest req = new CreateTaskRequest(
                "Review Compliance Standards",
                "Review the latest update to governance documentation.",
                employeeUser.getId(),
                null,
                null,
                "HIGH",
                Instant.now().plusSeconds(86400)
        );

        String createRes = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Review Compliance Standards"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        TaskDto task = objectMapper.readValue(createRes, TaskDto.class);

        // 2. Fetch my tasks
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(task.id().toString()));

        // 3. Advance status: OPEN -> IN_PROGRESS
        mockMvc.perform(patch("/api/v1/tasks/" + task.id() + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "IN_PROGRESS",
                                "version", task.version()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 4. Post comment
        mockMvc.perform(post("/api/v1/tasks/" + task.id() + "/comments")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Work on this is progressing smoothly."))))
                .andExpect(status().isNoContent());
    }

    @Test
    void notificationUnreadCountAndMarkRead() throws Exception {
        Notification n = new Notification(
                employeeUser.getId(),
                "TASK_ASSIGNED",
                "New Task Assigned",
                "You have been assigned to Review Compliance Standards",
                Map.of("url", "/tasks"),
                "task-assigned-" + employeeUser.getId()
        );
        n = notificationRepository.save(n);

        // Check unread count
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Mark read
        mockMvc.perform(post("/api/v1/notifications/" + n.getId() + "/mark-read")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNoContent());

        // Unread count should now be 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
