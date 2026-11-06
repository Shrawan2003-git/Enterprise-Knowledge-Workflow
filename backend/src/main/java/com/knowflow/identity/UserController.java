package com.knowflow.identity;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDto>> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateUserStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status field is required");
        }
        return ResponseEntity.ok(userService.updateUserStatus(id, status));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> assignRoles(
            @PathVariable UUID id,
            @RequestBody Map<String, Set<String>> body) {
        Set<String> roles = body.get("roles");
        if (roles == null) {
            throw new IllegalArgumentException("roles array is required");
        }
        return ResponseEntity.ok(userService.assignRoles(id, roles));
    }

    @PatchMapping("/{id}/department")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> assignDepartment(
            @PathVariable UUID id,
            @RequestBody Map<String, UUID> body) {
        UUID departmentId = body.get("departmentId");
        return ResponseEntity.ok(userService.assignDepartment(id, departmentId));
    }
}
