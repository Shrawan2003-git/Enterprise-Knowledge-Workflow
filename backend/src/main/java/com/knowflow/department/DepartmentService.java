package com.knowflow.department;

import com.knowflow.audit.AuditService;
import com.knowflow.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public DepartmentService(DepartmentRepository departmentRepository, AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> listAll() {
        return departmentRepository.findAll().stream()
                .map(DepartmentDto::fromEntity)
                .toList();
    }

    public DepartmentDto createDepartment(CreateDepartmentRequest request) {
        if (departmentRepository.existsByName(request.name())) {
            throw new IllegalStateException("Department with name '" + request.name() + "' already exists");
        }

        Department dept = new Department(request.name(), request.description());
        Department saved = departmentRepository.save(dept);

        auditService.record(
                SecurityUtils.getRequiredCurrentUser().id(),
                "CREATE_DEPARTMENT",
                "DEPARTMENT",
                saved.getId(),
                Map.of("name", saved.getName())
        );

        return DepartmentDto.fromEntity(saved);
    }

    public DepartmentDto updateDepartment(UUID id, CreateDepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found with ID: " + id));

        if (!dept.getName().equalsIgnoreCase(request.name()) && departmentRepository.existsByName(request.name())) {
            throw new IllegalStateException("Department with name '" + request.name() + "' already exists");
        }

        dept.setName(request.name());
        dept.setDescription(request.description());
        dept.setUpdatedAt(Instant.now());

        auditService.record(
                SecurityUtils.getRequiredCurrentUser().id(),
                "UPDATE_DEPARTMENT",
                "DEPARTMENT",
                dept.getId(),
                Map.of("name", dept.getName())
        );

        return DepartmentDto.fromEntity(dept);
    }
}
