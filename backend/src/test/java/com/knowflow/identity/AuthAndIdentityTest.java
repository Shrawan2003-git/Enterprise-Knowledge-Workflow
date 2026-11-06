package com.knowflow.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndIdentityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role adminRole = roleRepository.save(new Role("ADMIN", "Admin role"));
        Role employeeRole = roleRepository.save(new Role("EMPLOYEE", "Employee role"));

        User admin = new User("admin@test.com", "Admin User", passwordEncoder.encode("Secret123!"), null);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        User employee = new User("employee@test.com", "Employee User", passwordEncoder.encode("Secret123!"), null);
        employee.setRoles(Set.of(employeeRole));
        userRepository.save(employee);

        User inactive = new User("inactive@test.com", "Inactive User", passwordEncoder.encode("Secret123!"), null);
        inactive.setStatus("INACTIVE");
        inactive.setRoles(Set.of(employeeRole));
        userRepository.save(inactive);
    }

    @Test
    void loginSuccessReturnsTokensAndUserInfo() throws Exception {
        LoginRequest request = new LoginRequest("admin@test.com", "Secret123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("admin@test.com"));
    }

    @Test
    void loginFailureReturnsGenericUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("admin@test.com", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void inactiveUserLoginIsRejected() throws Exception {
        LoginRequest request = new LoginRequest("inactive@test.com", "Secret123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotationInvalidatesOldToken() throws Exception {
        LoginRequest login = new LoginRequest("employee@test.com", "Secret123!");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse auth = objectMapper.readValue(loginResponse, AuthResponse.class);
        String oldRefreshToken = auth.refreshToken();

        // Perform first refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(oldRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Attempting to reuse old refresh token should be rejected (revoked)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMeDerivesIdentityFromJwtToken() throws Exception {
        LoginRequest login = new LoginRequest("employee@test.com", "Secret123!");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();

        AuthResponse auth = objectMapper.readValue(loginResponse, AuthResponse.class);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("employee@test.com"))
                .andExpect(jsonPath("$.displayName").value("Employee User"));
    }

    @Test
    void unauthenticatedUserCannotAccessMe() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessAdminAuditEndpoint() throws Exception {
        LoginRequest login = new LoginRequest("employee@test.com", "Secret123!");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();

        AuthResponse auth = objectMapper.readValue(loginResponse, AuthResponse.class);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAuditEndpoint() throws Exception {
        LoginRequest login = new LoginRequest("admin@test.com", "Secret123!");
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();

        AuthResponse auth = objectMapper.readValue(loginResponse, AuthResponse.class);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk());
    }
}
