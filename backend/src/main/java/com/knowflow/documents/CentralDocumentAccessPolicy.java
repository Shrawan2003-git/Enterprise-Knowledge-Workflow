package com.knowflow.documents;

import com.knowflow.security.CurrentUser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Standard Central Document Access Policy:
 * Evaluates Role, Department, Ownership, Classification, Document Status, and Explicit ACLs.
 */
@Component
@Primary
public class CentralDocumentAccessPolicy implements DocumentAccessPolicy {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;

    public CentralDocumentAccessPolicy(DocumentRepository documentRepository,
                                       DocumentPermissionRepository permissionRepository) {
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public PolicyResult canView(CurrentUser user, UUID documentId) {
        if (user == null) {
            return PolicyResult.deny("UNAUTHENTICATED", "Authentication required to access document");
        }

        // 1. ADMIN has global oversight
        if (user.isAdmin()) {
            return PolicyResult.allow();
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return PolicyResult.deny("NOT_FOUND", "Document not found");
        }

        // 2. Document Owner always has view rights (unless purged)
        if (user.id().equals(document.getOwnerId())) {
            return PolicyResult.allow();
        }

        // 3. Archived documents are invisible to standard users
        if ("ARCHIVED".equalsIgnoreCase(document.getStatus())) {
            return PolicyResult.deny("ARCHIVED", "Archived documents are restricted to owners and administrators");
        }

        // 4. Classification-based checks
        String classification = document.getClassification() != null ? document.getClassification().toUpperCase() : "INTERNAL";

        if ("PUBLIC".equals(classification)) {
            return PolicyResult.allow();
        }

        if ("INTERNAL".equals(classification)) {
            if (document.getDepartmentId() != null && document.getDepartmentId().equals(user.departmentId())) {
                return PolicyResult.allow();
            }
        }

        // 5. Explicit ACLs for CONFIDENTIAL and RESTRICTED documents
        List<DocumentPermission> permissions = permissionRepository.findByDocumentId(documentId);
        boolean hasExplicitView = permissions.stream().anyMatch(perm -> 
            (perm.getPrincipalType().equalsIgnoreCase("USER") && perm.getPrincipalId().equals(user.id())) ||
            (perm.getPrincipalType().equalsIgnoreCase("DEPARTMENT") && user.departmentId() != null && perm.getPrincipalId().equals(user.departmentId()))
        );

        if (hasExplicitView) {
            return PolicyResult.allow();
        }

        return PolicyResult.deny("ACCESS_DENIED", "You do not have authorization to view this document");
    }

    @Override
    public PolicyResult canDownload(CurrentUser user, UUID documentId, Integer versionNumber) {
        // Must be authorized to view first
        PolicyResult viewResult = canView(user, documentId);
        if (!viewResult.allowed()) {
            return viewResult;
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document != null && document.isExpired(Instant.now())) {
            if (!user.isAdmin() && !user.id().equals(document.getOwnerId())) {
                return PolicyResult.deny("EXPIRED", "Document has expired and cannot be downloaded by general users");
            }
        }

        return PolicyResult.allow();
    }

    @Override
    public PolicyResult canEdit(CurrentUser user, UUID documentId) {
        if (user == null) {
            return PolicyResult.deny("UNAUTHENTICATED", "Authentication required");
        }
        if (user.isAdmin()) {
            return PolicyResult.allow();
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return PolicyResult.deny("NOT_FOUND", "Document not found");
        }

        if (user.id().equals(document.getOwnerId())) {
            return PolicyResult.allow();
        }

        if (user.isKnowledgeManager() && document.getDepartmentId() != null && document.getDepartmentId().equals(user.departmentId())) {
            return PolicyResult.allow();
        }

        List<DocumentPermission> permissions = permissionRepository.findByDocumentId(documentId);
        boolean hasEditPerm = permissions.stream().anyMatch(perm ->
            perm.getPrincipalType().equalsIgnoreCase("USER") &&
            perm.getPrincipalId().equals(user.id()) &&
            ("EDIT".equalsIgnoreCase(perm.getPermissionLevel()) || "MANAGE".equalsIgnoreCase(perm.getPermissionLevel()))
        );

        if (hasEditPerm) {
            return PolicyResult.allow();
        }

        return PolicyResult.deny("EDIT_FORBIDDEN", "Edit permission required");
    }

    @Override
    public PolicyResult canManagePermissions(CurrentUser user, UUID documentId) {
        if (user == null) return PolicyResult.deny("UNAUTHENTICATED", "Authentication required");
        if (user.isAdmin()) return PolicyResult.allow();

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) return PolicyResult.deny("NOT_FOUND", "Document not found");

        if (user.id().equals(document.getOwnerId())) {
            return PolicyResult.allow();
        }

        List<DocumentPermission> permissions = permissionRepository.findByDocumentId(documentId);
        boolean hasManagePerm = permissions.stream().anyMatch(perm ->
            perm.getPrincipalType().equalsIgnoreCase("USER") &&
            perm.getPrincipalId().equals(user.id()) &&
            "MANAGE".equalsIgnoreCase(perm.getPermissionLevel())
        );

        if (hasManagePerm) {
            return PolicyResult.allow();
        }

        return PolicyResult.deny("MANAGE_FORBIDDEN", "Administrative or Document Custodian privilege required");
    }

    @Override
    public PolicyResult canApprove(CurrentUser user, UUID documentId, UUID versionId) {
        if (user == null) return PolicyResult.deny("UNAUTHENTICATED", "Authentication required");
        if (user.isApprover() || user.isAdmin()) {
            return PolicyResult.allow();
        }
        return PolicyResult.deny("APPROVER_REQUIRED", "Approver role required");
    }
}
