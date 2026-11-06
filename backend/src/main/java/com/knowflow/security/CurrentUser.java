package com.knowflow.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable security principal abstraction resolved strictly from the authenticated SecurityContext.
 *
 * <p>Never instantiate or accept this from client request parameters.
 */
public record CurrentUser(
        UUID id,
        String email,
        String displayName,
        UUID departmentId,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        if (role == null) return false;
        String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;
        return roles.contains(normalized);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isKnowledgeManager() {
        return hasRole("KNOWLEDGE_MANAGER");
    }

    public boolean isApprover() {
        return hasRole("APPROVER");
    }
}
