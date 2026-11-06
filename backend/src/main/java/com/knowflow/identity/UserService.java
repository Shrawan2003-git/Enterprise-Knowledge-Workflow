package com.knowflow.identity;

import com.knowflow.audit.AuditService;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUserProfile() {
        CurrentUser current = SecurityUtils.getRequiredCurrentUser();
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return UserDto.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserDto::fromEntity);
    }

    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalStateException("User with email '" + request.email() + "' already exists");
        }

        User user = new User(
                request.email(),
                request.displayName(),
                passwordEncoder.encode(request.password()),
                request.departmentId()
        );

        if (request.roles() != null && !request.roles().isEmpty()) {
            Set<Role> roles = request.roles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                            .orElseGet(() -> roleRepository.save(new Role(roleName, "Auto-created role"))))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        } else {
            Role employeeRole = roleRepository.findByName("EMPLOYEE")
                    .orElseGet(() -> roleRepository.save(new Role("EMPLOYEE", "Standard employee role")));
            user.setRoles(Set.of(employeeRole));
        }

        User saved = userRepository.save(user);

        auditService.record(
                SecurityUtils.getCurrentUser() != null ? SecurityUtils.getCurrentUser().id() : null,
                "CREATE_USER",
                "USER",
                saved.getId(),
                Map.of("email", saved.getEmail())
        );

        return UserDto.fromEntity(saved);
    }

    public UserDto updateUserStatus(UUID userId, String newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (!List.of("ACTIVE", "INACTIVE", "SUSPENDED").contains(newStatus.toUpperCase())) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        String oldStatus = user.getStatus();
        user.setStatus(newStatus.toUpperCase());
        user.setUpdatedAt(Instant.now());

        auditService.record(
                SecurityUtils.getRequiredCurrentUser().id(),
                "UPDATE_USER_STATUS",
                "USER",
                user.getId(),
                Map.of("oldStatus", oldStatus, "newStatus", user.getStatus())
        );

        return UserDto.fromEntity(user);
    }

    public UserDto assignRoles(UUID userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        Set<Role> roles = roleNames.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName, "Role: " + roleName))))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        user.setUpdatedAt(Instant.now());

        auditService.record(
                SecurityUtils.getRequiredCurrentUser().id(),
                "ASSIGN_ROLES",
                "USER",
                user.getId(),
                Map.of("assignedRoles", roleNames)
        );

        return UserDto.fromEntity(user);
    }

    public UserDto assignDepartment(UUID userId, UUID departmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        user.setDepartmentId(departmentId);
        user.setUpdatedAt(Instant.now());

        auditService.record(
                SecurityUtils.getRequiredCurrentUser().id(),
                "ASSIGN_DEPARTMENT",
                "USER",
                user.getId(),
                Map.of("departmentId", departmentId != null ? departmentId.toString() : "null")
        );

        return UserDto.fromEntity(user);
    }
}
