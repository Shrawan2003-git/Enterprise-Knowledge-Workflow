package com.knowflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public static CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser) {
            return currentUser;
        }

        return null;
    }

    public static CurrentUser getRequiredCurrentUser() {
        CurrentUser user = getCurrentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated user context is required");
        }
        return user;
    }
}
