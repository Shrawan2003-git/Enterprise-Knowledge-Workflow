package com.knowflow.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String status,
        UUID departmentId,
        Set<String> roles,
        Instant createdAt
) {
    public static UserDto fromEntity(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getDepartmentId(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
