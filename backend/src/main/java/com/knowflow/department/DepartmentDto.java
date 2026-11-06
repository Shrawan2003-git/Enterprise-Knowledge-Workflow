package com.knowflow.department;

import java.time.Instant;
import java.util.UUID;

public record DepartmentDto(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
    public static DepartmentDto fromEntity(Department entity) {
        return new DepartmentDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }
}
