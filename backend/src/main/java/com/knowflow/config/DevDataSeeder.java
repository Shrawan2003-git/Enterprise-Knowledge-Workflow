package com.knowflow.config;

import com.knowflow.department.Department;
import com.knowflow.department.DepartmentRepository;
import com.knowflow.identity.Role;
import com.knowflow.identity.RoleRepository;
import com.knowflow.identity.User;
import com.knowflow.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("!test")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${KNOWFLOW_DEV_PASSWORD:Password123!}")
    private String devPassword;

    public DevDataSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("Seeding initial development roles, departments, and synthetic users...");

        // 1. Roles
        Role roleAdmin = roleRepository.save(new Role("ADMIN", "System administrator"));
        Role roleKm = roleRepository.save(new Role("KNOWLEDGE_MANAGER", "Knowledge and document manager"));
        Role roleApprover = roleRepository.save(new Role("APPROVER", "Document approver"));
        Role roleEmployee = roleRepository.save(new Role("EMPLOYEE", "Standard employee"));
        roleRepository.save(new Role("SYSTEM_WORKER", "Background service worker"));

        // 2. Departments
        Department engineering = departmentRepository.save(new Department("Engineering", "Software & Systems"));
        Department operations = departmentRepository.save(new Department("Operations", "Field & Plant Ops"));
        Department legal = departmentRepository.save(new Department("Legal & Compliance", "Policy Governance"));

        String hash = passwordEncoder.encode(devPassword);

        // 3. Synthetic Users (one per primary role)
        createUser("admin@knowflow.local", "System Admin", hash, engineering, Set.of(roleAdmin));
        createUser("km@knowflow.local", "Knowledge Manager", hash, operations, Set.of(roleKm));
        createUser("approver@knowflow.local", "Lead Approver", hash, legal, Set.of(roleApprover));
        createUser("employee@knowflow.local", "Standard Employee", hash, engineering, Set.of(roleEmployee));

        log.info("Development seeding completed successfully.");
    }

    private void createUser(String email, String name, String hash, Department dept, Set<Role> roles) {
        User user = new User(email, name, hash, dept != null ? dept.getId() : null);
        user.setRoles(roles);
        userRepository.save(user);
    }
}
