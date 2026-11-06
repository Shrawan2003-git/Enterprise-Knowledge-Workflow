package com.knowflow.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DocumentPermissionDto(
        @NotBlank String principalType,
        @NotNull UUID principalId,
        @NotBlank String permissionLevel
) {
    public static DocumentPermissionDto fromEntity(DocumentPermission p) {
        return new DocumentPermissionDto(p.getPrincipalType(), p.getPrincipalId(), p.getPermissionLevel());
    }
}
