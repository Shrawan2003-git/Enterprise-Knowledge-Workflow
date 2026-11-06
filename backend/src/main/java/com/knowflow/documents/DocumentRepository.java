package com.knowflow.documents;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d LEFT JOIN FETCH d.currentVersion LEFT JOIN FETCH d.category WHERE d.id = :id")
    Optional<Document> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT d FROM Document d WHERE d.status != 'ARCHIVED'")
    Page<Document> findAllActive(Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.departmentId = :deptId AND d.status != 'ARCHIVED'")
    Page<Document> findByDepartmentActive(@Param("deptId") UUID deptId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.ownerId = :ownerId")
    Page<Document> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);
}
