# ADR-001: Modular Monolith Architecture

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow is being built by a solo developer as an enterprise-grade knowledge and workflow
platform. The system has 15+ logical modules (identity, department, documents, ingestion,
search, approvals, tasks, notifications, copilot, tools, audit, reporting, common, config,
security). We need an architecture that:

1. Keeps deployment and operations simple for a single developer.
2. Enforces clear module boundaries so the codebase stays maintainable as it grows.
3. Allows future extraction of modules into services **if and when** measured data justifies it.
4. Supports transactional consistency across modules without distributed transactions.

## Decision

Build KnowFlow as a **single Spring Boot application** organized by **feature packages** under
`com.knowflow`. Each module gets its own top-level package:

```
com.knowflow.identity
com.knowflow.department
com.knowflow.documents
com.knowflow.ingestion
com.knowflow.search
com.knowflow.approvals
com.knowflow.tasks
com.knowflow.notifications
com.knowflow.copilot
com.knowflow.tools
com.knowflow.audit
com.knowflow.reporting
com.knowflow.common
com.knowflow.config
com.knowflow.security
```

### Module Interaction Rules

| Rule | Enforcement |
|------|-------------|
| No circular dependencies between modules | ArchUnit test (`ModuleBoundaryTest`) |
| Modules communicate via public service interfaces, never by accessing another module's repositories directly | Code review + ArchUnit |
| Shared kernel code lives in `common` only | ArchUnit |
| Each module owns its own DB tables; no cross-module foreign keys except to `identity.users` and `department.departments` | Flyway migration review |
| Events between modules use Spring's `ApplicationEventPublisher` (in-process) | Convention |

### Package Structure per Module

```
com.knowflow.<module>/
  ├── controller/       # REST controllers (thin, delegation only)
  ├── dto/              # Request/response records
  ├── service/          # Business logic
  ├── domain/           # JPA entities (never exposed outside module)
  ├── repository/       # Spring Data JPA repositories
  ├── event/            # Domain events (published/consumed)
  └── exception/        # Module-specific exceptions
```

### Service Extraction Criteria

A module should be extracted into a separate service **only when ALL** of the following are true:

1. The module needs **independent deployment** (e.g., different release cadence).
2. The module needs **independent scaling** (e.g., ingestion workers need 4x the CPU of the
   web tier, measured under production load).
3. A **separate team** will own the module.
4. **Measured performance data** (latency percentiles, throughput, resource utilization)
   justifies the operational complexity of a separate service.

Until these criteria are met, the monolith stays monolithic.

## Alternatives Considered

### Microservices from Day 1

| Aspect | Assessment |
|--------|------------|
| Pros | Independent scaling, independent deployment, technology diversity |
| Cons | Massive operational overhead for a solo developer: service discovery, distributed tracing, API gateways, distributed transactions, network partitions, independent CI/CD pipelines, container orchestration |
| Verdict | **Rejected.** Premature complexity. The team size (1) and scale (initial deployment) do not justify the overhead. |

### Layered Monolith (controller → service → repository)

| Aspect | Assessment |
|--------|------------|
| Pros | Simple, well-understood pattern |
| Cons | Poor module boundaries — services from different features end up in the same package, leading to spaghetti dependencies. Makes future extraction nearly impossible without major refactoring. |
| Verdict | **Rejected.** Feature-based packaging provides the same simplicity with much better boundaries. |

### Hexagonal Architecture (Ports & Adapters)

| Aspect | Assessment |
|--------|------------|
| Pros | Clean separation of domain from infrastructure |
| Cons | Adds indirection layers (ports, adapters, use-case classes) that increase boilerplate. For a Spring Boot app with well-defined module boundaries, the benefit is marginal. |
| Verdict | **Rejected for full adoption.** We selectively apply the principle at external boundaries (see ADR-003, ADR-006, ADR-007) by using interfaces for FileStorage, ChatModelGateway, EmbeddingService, NotificationSender, and JobDispatcher. |

## Consequences

### Positive

- **Simple operations:** One artifact to build, deploy, and monitor.
- **Transactional safety:** `@Transactional` works across modules without distributed
  transaction coordination.
- **Fast local development:** `./mvnw spring-boot:run` starts the entire system.
- **Clear boundaries:** ArchUnit tests catch dependency violations at build time, not in
  production.
- **Extraction-ready:** Feature packages with interface-based inter-module communication make
  future extraction straightforward if needed.

### Negative

- **Single scaling unit:** Cannot scale ingestion workers independently from the web tier
  without the worker profile approach (mitigated by Spring profiles — see ADR-007).
- **Blast radius:** A bug in one module can bring down the entire application (mitigated by
  circuit breakers on external calls and health checks).
- **Build time grows:** As the codebase grows, full builds take longer (mitigated by
  incremental compilation and parallel test execution).

### Obligations

- [ ] Create `ModuleBoundaryTest` using ArchUnit before the second module is implemented.
- [ ] Document allowed module dependencies in a dependency diagram
  (see [architecture.md](../architecture.md)).
- [ ] Review extraction criteria annually or when team size exceeds 3 developers.

---

*Cross-references:* [ADR-007](ADR-007-database-backed-job-queue.md) (worker profiles),
[ADR-003](ADR-003-file-storage.md) (external boundary interfaces),
[ADR-006](ADR-006-llm-provider.md) (ChatModelGateway interface)
