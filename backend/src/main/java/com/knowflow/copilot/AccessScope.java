package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;

import java.util.UUID;

public record AccessScope(
        UUID userId,
        UUID departmentId,
        boolean isAdmin,
        String pipelineVersion
) {
    public static AccessScope fromUser(CurrentUser user, String pipelineVersion) {
        String pVer = (pipelineVersion != null && !pipelineVersion.isBlank()) ? pipelineVersion : "v1";
        if (user == null) {
            return new AccessScope(null, null, false, pVer);
        }
        return new AccessScope(user.id(), user.departmentId(), user.isAdmin(), pVer);
    }
}
