package com.knowflow.documents;

import com.knowflow.security.CurrentUser;

import java.util.UUID;

/**
 * Result outcome of an authorization policy evaluation.
 */
public record PolicyResult(
        boolean allowed,
        String reasonCode,
        String message
) {
    public static PolicyResult allow() {
        return new PolicyResult(true, "AUTHORIZED", "Access permitted");
    }

    public static PolicyResult deny(String reasonCode, String message) {
        return new PolicyResult(false, reasonCode, message);
    }
}
