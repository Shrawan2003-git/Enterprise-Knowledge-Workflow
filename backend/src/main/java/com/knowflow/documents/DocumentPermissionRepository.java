package com.knowflow.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, UUID> {
    List<DocumentPermission> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
