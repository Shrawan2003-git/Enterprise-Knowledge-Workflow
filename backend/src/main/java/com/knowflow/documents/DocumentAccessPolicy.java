package com.knowflow.documents;

import com.knowflow.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central authorization policy for document-related actions.
 * Download, search, retrieval, citations, and AI tools ALL enforce this contract server-side.
 */
public interface DocumentAccessPolicy {

    PolicyResult canView(CurrentUser user, UUID documentId);

    PolicyResult canDownload(CurrentUser user, UUID documentId, Integer versionNumber);

    PolicyResult canEdit(CurrentUser user, UUID documentId);

    PolicyResult canManagePermissions(CurrentUser user, UUID documentId);

    PolicyResult canApprove(CurrentUser user, UUID documentId, UUID versionId);

    /**
     * Baseline deny-by-default placeholder for Phase 1.
     * Extended in Phase 2 with full departmental, role, classification, and status matrices.
     */
    @Component
    class DenyByDefaultDocumentAccessPolicy implements DocumentAccessPolicy {

        @Override
        public PolicyResult canView(CurrentUser user, UUID documentId) {
            if (user != null && user.isAdmin()) {
                return PolicyResult.allow();
            }
            return PolicyResult.deny("RESTRICTED_ACCESS", "Document access denied by default policy");
        }

        @Override
        public PolicyResult canDownload(CurrentUser user, UUID documentId, Integer versionNumber) {
            if (user != null && user.isAdmin()) {
                return PolicyResult.allow();
            }
            return PolicyResult.deny("RESTRICTED_DOWNLOAD", "Document download denied by default policy");
        }

        @Override
        public PolicyResult canEdit(CurrentUser user, UUID documentId) {
            if (user != null && (user.isAdmin() || user.isKnowledgeManager())) {
                return PolicyResult.allow();
            }
            return PolicyResult.deny("EDIT_FORBIDDEN", "Edit permission required");
        }

        @Override
        public PolicyResult canManagePermissions(CurrentUser user, UUID documentId) {
            if (user != null && user.isAdmin()) {
                return PolicyResult.allow();
            }
            return PolicyResult.deny("MANAGE_FORBIDDEN", "Administrative privilege required to manage permissions");
        }

        @Override
        public PolicyResult canApprove(CurrentUser user, UUID documentId, UUID versionId) {
            if (user != null && user.isApprover()) {
                return PolicyResult.allow();
            }
            return PolicyResult.deny("APPROVER_REQUIRED", "Approver role required");
        }
    }
}
