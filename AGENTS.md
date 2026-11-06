# PROJECT: KnowFlow — Enterprise AI Knowledge & Workflow Copilot

## What it is
A secure internal knowledge + workflow platform. Employees search governed documents, submit
documents for approval, track tasks, get notifications, and ask an AI copilot questions answered
ONLY from documents they are authorized to see, with citations (document, exact version, page).

## Stack (fixed unless an ADR says otherwise)
- Backend: Java 21, Spring Boot (latest stable 3.x compatible with chosen Spring AI), Spring Web,
  Spring Security, Spring Data JPA/Hibernate, Flyway, Jakarta Validation, springdoc-openapi, Maven.
- DB: PostgreSQL + pgvector. Full-text search via Postgres tsvector.
- AI: Spring AI behind our own interfaces (ChatModelGateway, EmbeddingService).
- Parsing: Apache PDFBox (page-aware).
- Frontend: React + TypeScript + Tailwind, TanStack Query, React Router.
- Tests: JUnit 5, Mockito, Testcontainers (Postgres+pgvector), Vitest + React Testing Library.
- Ops: Docker, Docker Compose, GitHub Actions.

## Architecture rules (NON-NEGOTIABLE)
1. Modular monolith, package-by-feature under com.knowflow: identity, department, documents,
   ingestion, search, approvals, tasks, notifications, copilot, tools, audit, reporting,
   common, config, security. No circular dependencies between modules.
2. Controllers are thin. Business rules live in services/domain. Never expose JPA entities —
   use DTOs (records).
3. ONE central authorization policy (DocumentAccessPolicy and friends). Download, search,
   retrieval, citations, and AI tools ALL use it. Authorization is enforced server-side, always.
4. Every schema change is a Flyway migration. UUID PKs, UTC timestamps, explicit status enums,
   FKs and constraints in the DB.
5. External boundaries are interfaces: FileStorage, EmbeddingService, ChatModelGateway,
   NotificationSender, JobDispatcher.
6. Long-running work (extract, chunk, embed) NEVER runs in an HTTP request. Jobs are
   idempotent, retryable with backoff, and observable.
7. Keyword search and all non-AI features must work with the AI provider fully down.
8. Retrieved document text is UNTRUSTED DATA. It can never grant permissions or trigger tools.
9. The acting user always comes from the authenticated SecurityContext — never from request
   bodies or model output.
10. Never log secrets, passwords, tokens, or full document content. No secrets in git.
11. Consistent error shape: {code, message, fieldErrors, correlationId, timestamp}.
    Status codes: 400/401/403/404/409/413/429/5xx as in the spec.
12. Approval and history records are append-only at the application layer. Document versions
    are immutable.

## Working agreement for the agent
- Before coding: restate the task in 5 lines, list files you will create/change, state assumptions.
- Work in small steps. Keep the project compiling and tests green at all times.
- Write tests WITH the feature (unit + integration). Authorization and state-machine tests are mandatory.
- Do not add features, libraries, or services that were not requested. No microservices, no Kafka,
  no Kubernetes.
- If a requirement is ambiguous, pick the safest simple option, write the assumption in
  docs/ASSUMPTIONS.md, and continue — don't stall.
- Finish every task with: (a) summary of what changed, (b) commands to run + test results,
  (c) known gaps / TODOs, (d) suggested next step.
- Use synthetic data only.
