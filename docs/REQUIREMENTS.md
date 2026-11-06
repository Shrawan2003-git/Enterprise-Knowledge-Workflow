# KnowFlow — User Stories & Acceptance Criteria

> **Document Status:** Living document — updated as new stories are refined.
> **Related Docs:** [ROLE_MATRIX.md](./ROLE_MATRIX.md) · [ASSUMPTIONS.md](./ASSUMPTIONS.md)

---

## 1. Identity & Account Management

### US-1.1 · Login

**As a** user of any role,
**I want** to log in with my email and password,
**so that** I can access the platform features I am authorized for.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/auth/login` with valid credentials returns 200 with a JWT access token and an HTTP-only refresh token cookie. |
| AC-2 | The JWT contains `sub` (user UUID), `roles` (list), `departmentId`, and `exp` (expiry). |
| AC-3 | Invalid credentials return 401 with a generic message: _"Invalid email or password."_ — no indication of whether the email exists. |
| AC-4 | A deactivated account returns 401 with the same generic message. |
| AC-5 | After 5 consecutive failed attempts for the same email within 15 minutes, further attempts for that email are rejected with 429 for the remainder of the window. |
| AC-6 | Successful login resets the failed-attempt counter for that email. |
| AC-7 | An `AUDIT_LOGIN_SUCCESS` or `AUDIT_LOGIN_FAILURE` event is recorded for every attempt. |

---

### US-1.2 · Logout

**As a** logged-in user,
**I want** to log out,
**so that** my session is invalidated and cannot be reused.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/auth/logout` clears the refresh token cookie and returns 204. |
| AC-2 | The revoked refresh token is added to a deny-list; subsequent refresh attempts with it return 401. |
| AC-3 | The short-lived access token naturally expires (no server-side revocation required for JWTs). |

---

### US-1.3 · View My Profile

**As a** logged-in user,
**I want** to view my profile information,
**so that** I can verify my account details.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/users/me` returns 200 with `{id, email, firstName, lastName, roles, department, createdAt}`. |
| AC-2 | No sensitive fields (password hash, internal flags) are exposed. |
| AC-3 | Unauthenticated requests return 401. |

---

### US-1.4 · Admin Creates a User

**As an** ADMIN,
**I want** to create user accounts,
**so that** new employees can access the system.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/admin/users` with `{email, firstName, lastName, roles, departmentId, temporaryPassword}` returns 201 with the created user DTO. |
| AC-2 | Duplicate email returns 409. |
| AC-3 | `roles` must be a non-empty subset of `{EMPLOYEE, KNOWLEDGE_MANAGER, APPROVER, ADMIN}`. `SYSTEM_WORKER` cannot be assigned via this endpoint. |
| AC-4 | Non-ADMIN callers receive 403. |
| AC-5 | An `AUDIT_USER_CREATED` event is recorded with the acting admin's ID. |

---

### US-1.5 · Admin Deactivates a User

**As an** ADMIN,
**I want** to deactivate a user account,
**so that** former employees lose access without deleting their data.

| # | Acceptance Criteria |
|---|---|
| AC-1 | PATCH `/api/admin/users/{userId}/deactivate` returns 200 with updated user DTO (`active: false`). |
| AC-2 | A deactivated user's existing refresh tokens are revoked. |
| AC-3 | Subsequent login attempts by the deactivated user return 401. |
| AC-4 | An admin cannot deactivate their own account (400). |
| AC-5 | An `AUDIT_USER_DEACTIVATED` event is recorded. |

---

### US-1.6 · Admin Assigns Roles

**As an** ADMIN,
**I want** to assign or remove roles from a user,
**so that** I can control their level of access.

| # | Acceptance Criteria |
|---|---|
| AC-1 | PUT `/api/admin/users/{userId}/roles` with `{roles: [...]}` replaces the user's roles and returns 200. |
| AC-2 | At least one role must remain; empty set returns 400. |
| AC-3 | `SYSTEM_WORKER` cannot be assigned or removed via this endpoint. |
| AC-4 | An `AUDIT_ROLES_CHANGED` event is recorded listing old and new roles. |

---

### US-1.7 · Admin Manages Departments

**As an** ADMIN,
**I want** to create, rename, and deactivate departments,
**so that** the organizational structure is reflected in access policies.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/admin/departments` creates a department; returns 201. |
| AC-2 | PUT `/api/admin/departments/{deptId}` renames it; returns 200. |
| AC-3 | PATCH `/api/admin/departments/{deptId}/deactivate` marks it inactive; returns 200. Users in the department are not automatically deactivated but must be reassigned to upload new documents. |
| AC-4 | Duplicate department names return 409. |
| AC-5 | An `AUDIT_DEPARTMENT_CHANGED` event is recorded. |

---

### US-1.8 · Password Reset

**As a** user who has forgotten my password,
**I want** to request a reset link,
**so that** I can regain access to my account.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/auth/password-reset-request` with `{email}` always returns 202 regardless of whether the email exists (prevents enumeration). |
| AC-2 | If the email exists, a time-limited (30 min) single-use token is generated and dispatched via `NotificationSender`. |
| AC-3 | POST `/api/auth/password-reset` with `{token, newPassword}` validates the token, updates the password hash, revokes all existing refresh tokens, and returns 200. |
| AC-4 | Expired or reused tokens return 400 with a generic message. |
| AC-5 | New password must meet minimum complexity (≥ 12 chars, mix of upper/lower/digit/special). |

---

## 2. Document Library & Versioning

### US-2.1 · Upload a Document

**As a** KNOWLEDGE_MANAGER or ADMIN,
**I want** to upload a document (PDF) with metadata,
**so that** it enters the knowledge base for search and governance.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/documents` (multipart: file + metadata JSON) returns 201 with `{documentId, versionId, status: DRAFT}`. |
| AC-2 | Metadata includes: `title` (required, ≤ 255 chars), `description` (optional, ≤ 2000 chars), `categoryId` (required), `tags` (optional list), `classification` (required, one of `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`), `departmentId` (required). |
| AC-3 | File size limit: 50 MB. Exceeding returns 413. |
| AC-4 | Only `application/pdf` MIME type is accepted; others return 400. |
| AC-5 | The file is stored via `FileStorage`; the path/key is persisted, never exposed to clients. |
| AC-6 | An async ingestion job (`EXTRACT → CHUNK → EMBED`) is enqueued for the new version. |
| AC-7 | The uploader is automatically the owner of the document. |
| AC-8 | An `AUDIT_DOCUMENT_UPLOADED` event is recorded. |
| AC-9 | EMPLOYEE callers receive 403. |

---

### US-2.2 · View Document Metadata

**As an** authorized user,
**I want** to view a document's metadata and version history,
**so that** I can understand its contents and lineage.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/documents/{docId}` returns 200 with metadata DTO including `title, description, category, tags, classification, department, owner, status, currentVersionId, createdAt, updatedAt, expiresAt`. |
| AC-2 | GET `/api/documents/{docId}/versions` returns a paginated list of version summaries `{versionId, versionNumber, uploadedAt, uploadedBy, pageCount, ingestionStatus}`. |
| AC-3 | If the user has no access to the document, the endpoint returns 404 (not 403). |
| AC-4 | Version list is ordered by `versionNumber` descending. |

---

### US-2.3 · Create a New Version

**As a** KNOWLEDGE_MANAGER or ADMIN (or document owner),
**I want** to upload a new version of an existing document,
**so that** the knowledge base stays current while preserving history.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/documents/{docId}/versions` (multipart: new PDF) returns 201 with `{versionId, versionNumber}`. |
| AC-2 | The new version number is `max(existing) + 1`. |
| AC-3 | Previous versions remain immutable and downloadable. |
| AC-4 | The document's `status` resets to `DRAFT` (a new version must go through approval again, if required). |
| AC-5 | A new ingestion job is enqueued for the new version. |
| AC-6 | Old version embeddings remain until the new version is fully ingested to avoid a search gap. |

---

### US-2.4 · Update Document Metadata

**As the** document owner, a KNOWLEDGE_MANAGER, or an ADMIN,
**I want** to update a document's metadata (title, description, tags, category, classification, expiry),
**so that** the document remains accurately described and classified.

| # | Acceptance Criteria |
|---|---|
| AC-1 | PATCH `/api/documents/{docId}` with partial metadata returns 200 with updated DTO. |
| AC-2 | Only provided fields are updated; omitted fields are untouched. |
| AC-3 | Classification upgrades (e.g., `INTERNAL` → `CONFIDENTIAL`) are immediately enforced for subsequent access checks. |
| AC-4 | An `AUDIT_DOCUMENT_METADATA_UPDATED` event is recorded with old and new values. |
| AC-5 | Unauthorized callers receive 404. |

---

### US-2.5 · Archive / Restore a Document

**As a** KNOWLEDGE_MANAGER or ADMIN,
**I want** to archive a document and later restore it,
**so that** obsolete content is hidden from search without data loss.

| # | Acceptance Criteria |
|---|---|
| AC-1 | PATCH `/api/documents/{docId}/archive` sets `status = ARCHIVED` and returns 200. |
| AC-2 | Archived documents are excluded from search results and AI retrieval. |
| AC-3 | PATCH `/api/documents/{docId}/restore` sets `status` back to the pre-archive status (or `DRAFT` if unknown) and returns 200. |
| AC-4 | Archived documents remain downloadable to users with explicit permissions. |
| AC-5 | Respective audit events are recorded. |

---

### US-2.6 · Set Expiry Date

**As a** KNOWLEDGE_MANAGER or ADMIN,
**I want** to set an expiry date on a document,
**so that** outdated content is flagged and stakeholders are notified.

| # | Acceptance Criteria |
|---|---|
| AC-1 | `expiresAt` (ISO-8601 date, UTC) can be set during upload or metadata update. |
| AC-2 | A scheduled job runs daily and marks documents past their `expiresAt` as `EXPIRED`. |
| AC-3 | 30 days and 7 days before expiry, a notification is sent to the document owner and department KMs. |
| AC-4 | Expired documents are excluded from search and AI retrieval but remain downloadable. |
| AC-5 | Setting `expiresAt` to `null` removes the expiry. |

---

### US-2.7 · Categories & Tags Management

**As an** ADMIN,
**I want** to manage the list of document categories,
**so that** the taxonomy is consistent across the organization.

| # | Acceptance Criteria |
|---|---|
| AC-1 | CRUD endpoints at `/api/admin/categories` (POST, GET list, PUT `{catId}`, DELETE `{catId}`). |
| AC-2 | Deleting a category that has documents returns 409 with message: _"Category is in use."_ |
| AC-3 | Tags are free-form strings on documents; no admin endpoint needed for tags in v1. |
| AC-4 | Category names are unique (case-insensitive); duplicates return 409. |

---

## 3. Document Permissions & Access Control

### US-3.1 · Set Document Permissions

**As the** document owner, a KNOWLEDGE_MANAGER for the document's department, or an ADMIN,
**I want** to grant or revoke access to a document for specific users, groups, or departments,
**so that** only authorized people can view or manage it.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/documents/{docId}/permissions` with `{principalType: USER|GROUP|DEPARTMENT, principalId, level: VIEW|EDIT|MANAGE}` returns 201. |
| AC-2 | DELETE `/api/documents/{docId}/permissions/{permId}` removes the grant; returns 204. |
| AC-3 | GET `/api/documents/{docId}/permissions` returns all grants (only visible to users with MANAGE level or ADMIN). |
| AC-4 | EDIT implies VIEW; MANAGE implies EDIT + VIEW. |
| AC-5 | An `AUDIT_PERMISSION_CHANGED` event is recorded. |
| AC-6 | Unauthorized callers receive 404 for the document. |

---

### US-3.2 · Classification-Based Access

**As the** system,
**I want** to enforce classification-based visibility rules,
**so that** sensitive documents are restricted by default.

| # | Acceptance Criteria |
|---|---|
| AC-1 | `PUBLIC` documents are visible to all authenticated users. |
| AC-2 | `INTERNAL` documents are visible to all authenticated users. |
| AC-3 | `CONFIDENTIAL` documents require explicit permission (user, group, or department grant) OR ownership OR ADMIN role. |
| AC-4 | `RESTRICTED` documents require explicit permission (user or group grant only — department-level grants do not apply) OR ownership. ADMIN role alone is not sufficient. |
| AC-5 | Classification is evaluated server-side in `DocumentAccessPolicy` on every access path (view, download, search, AI retrieval). |

---

### US-3.3 · Download with Per-Request Authorization

**As an** authorized user,
**I want** to download a specific version of a document,
**so that** I can read it offline.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/documents/{docId}/versions/{versionId}/download` returns 200 with `Content-Type: application/pdf` and `Content-Disposition: attachment`. |
| AC-2 | Authorization is evaluated at the moment of the request — not cached from a previous check. |
| AC-3 | If the user has no access, the endpoint returns 404. |
| AC-4 | An `AUDIT_DOCUMENT_DOWNLOADED` event is recorded with `userId, documentId, versionId`. |

---

### US-3.4 · Access Policy Evaluation

**As the** system,
**I want** a single `DocumentAccessPolicy` component that evaluates access,
**so that** authorization logic is consistent across all features.

| # | Acceptance Criteria |
|---|---|
| AC-1 | The policy takes `(userId, documentId, requiredLevel)` and returns `boolean`. |
| AC-2 | Evaluation order: (1) document must exist and not be deleted, (2) user is owner → granted, (3) user has ADMIN role → granted for non-RESTRICTED, (4) explicit permission at required level or higher exists, (5) department grant (non-RESTRICTED), (6) classification allows. |
| AC-3 | The policy is invoked by: document detail, download, search result filtering, AI retrieval, citation rendering. |
| AC-4 | The policy is covered by unit tests for every branch/combination. |

---

## 4. Search & Discovery

### US-4.1 · Keyword Full-Text Search

**As an** EMPLOYEE (or any authenticated user),
**I want** to search for documents using keywords,
**so that** I can find relevant information quickly.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/search?q=...&filters...&page=0&size=20&sort=relevance` returns paginated results. |
| AC-2 | Search uses PostgreSQL `tsvector`/`tsquery` on `title`, `description`, and optionally `tags`. |
| AC-3 | Supported filters: `categoryId`, `departmentId`, `status`, `classification`, `ownerId`, `tags` (comma-separated), `uploadedAfter`, `uploadedBefore`. |
| AC-4 | Results include `{documentId, title, description (truncated), category, department, owner, status, classification, updatedAt, relevanceScore}`. |
| AC-5 | Results are ACL-filtered: only documents the requesting user can access are returned. |
| AC-6 | Total count reflects only accessible documents (no leakage of existence). |
| AC-7 | Sorting options: `relevance` (default), `updatedAt`, `title`. |

---

### US-4.2 · ACL-Filtered Results

**As the** system,
**I want** search results filtered at the SQL level using ACL joins,
**so that** unauthorized documents are never returned, counted, or hinted at.

| # | Acceptance Criteria |
|---|---|
| AC-1 | The search query joins against the permissions table and applies classification/role checks in the WHERE clause. |
| AC-2 | No post-fetch filtering — the SQL returns only accessible rows. |
| AC-3 | Snippets are generated only from accessible documents. |
| AC-4 | A user who cannot access a `CONFIDENTIAL` document does not see it in results, counts, or facets. |

---

### US-4.3 · Search Works When AI Is Down

**As an** EMPLOYEE,
**I want** keyword search to function normally even if the AI provider is unreachable,
**so that** I am never locked out of finding documents.

| # | Acceptance Criteria |
|---|---|
| AC-1 | Keyword search uses only PostgreSQL full-text search — no dependency on the embedding model or AI provider. |
| AC-2 | If the AI provider is down, the `/api/search` endpoint continues to return results normally. |
| AC-3 | A health-check endpoint reports AI provider status separately from search availability. |

---

## 5. Review & Approval Workflow

### US-5.1 · Submit Version for Review

**As a** document owner or KNOWLEDGE_MANAGER,
**I want** to submit a specific document version for review by a named approver,
**so that** the content is formally vetted before it becomes active.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/documents/{docId}/versions/{versionId}/reviews` with `{approverId}` returns 201 with `{reviewId, status: IN_REVIEW}`. |
| AC-2 | The document status transitions from `DRAFT` → `IN_REVIEW`. |
| AC-3 | The assigned approver must have the `APPROVER` role; otherwise 400. |
| AC-4 | The submitter cannot assign themselves as the approver (self-approval blocked); returns 400. |
| AC-5 | If the version is not the current version, returns 400: _"Only the current version can be submitted for review."_ |
| AC-6 | If a review is already pending for this document, returns 409. |
| AC-7 | A notification is sent to the assigned approver. |
| AC-8 | An `AUDIT_REVIEW_SUBMITTED` event is recorded. |

---

### US-5.2 · Approve / Reject / Request Changes

**As an** assigned APPROVER,
**I want** to approve, reject, or request changes on a submission,
**so that** only quality content is published.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/reviews/{reviewId}/decision` with `{decision: APPROVED|REJECTED|CHANGES_REQUESTED, comment}` returns 200. |
| AC-2 | `REJECTED` and `CHANGES_REQUESTED` require a non-blank `comment`; otherwise 400. |
| AC-3 | `APPROVED` transitions the document status to `APPROVED`. `REJECTED` transitions to `REJECTED`. `CHANGES_REQUESTED` transitions to `CHANGES_REQUESTED`. |
| AC-4 | Once a decision is recorded, a duplicate decision on the same review returns 409. |
| AC-5 | Only the assigned approver can make the decision; others receive 403. |
| AC-6 | If the version under review is no longer the current version (a new version was uploaded meanwhile), returns 409: _"This version is outdated; a newer version exists."_ |
| AC-7 | A notification is sent to the submitter with the decision and comment. |
| AC-8 | Decision and comment are appended as an immutable history record. |
| AC-9 | An `AUDIT_REVIEW_DECIDED` event is recorded. |

---

### US-5.3 · View Approval History

**As a** document owner, KNOWLEDGE_MANAGER, or ADMIN,
**I want** to view the full approval history of a document,
**so that** I have an audit trail of every review decision.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/documents/{docId}/reviews` returns a chronological list of `{reviewId, versionId, submittedBy, approver, status, decision, comment, submittedAt, decidedAt}`. |
| AC-2 | History records are append-only — no updates or deletes. |
| AC-3 | Unauthorized callers receive 404. |

---

### US-5.4 · Re-submit After Changes Requested

**As a** document owner,
**I want** to upload a new version and re-submit for review after changes were requested,
**so that** the feedback loop completes.

| # | Acceptance Criteria |
|---|---|
| AC-1 | After `CHANGES_REQUESTED`, the owner uploads a new version (US-2.3), which resets status to `DRAFT`. |
| AC-2 | The owner can then submit the new version for review (US-5.1) with the same or different approver. |
| AC-3 | The previous review record remains immutable in history. |

---

## 6. Tasks & Notifications

### US-6.1 · Create a Task

**As a** KNOWLEDGE_MANAGER, APPROVER, or ADMIN,
**I want** to create a task and assign it to a user,
**so that** work items are tracked to completion.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/tasks` with `{title, description, assigneeId, priority: LOW|MEDIUM|HIGH|CRITICAL, dueDate, linkedDocumentId?, linkedReviewId?}` returns 201. |
| AC-2 | `title` is required (≤ 255 chars). `assigneeId` is required and must reference an active user. |
| AC-3 | `dueDate` must be in the future; otherwise 400. |
| AC-4 | Initial status is `OPEN`. |
| AC-5 | A notification is sent to the assignee. |
| AC-6 | EMPLOYEE cannot create tasks (403). |

---

### US-6.2 · Task Status Transitions

**As the** task assignee or the task creator,
**I want** to update a task's status,
**so that** progress is visible.

| # | Acceptance Criteria |
|---|---|
| AC-1 | PATCH `/api/tasks/{taskId}/status` with `{status, completionNotes?}` returns 200. |
| AC-2 | Valid transitions: `OPEN → IN_PROGRESS`, `IN_PROGRESS → DONE`, `IN_PROGRESS → CANCELLED`, `OPEN → CANCELLED`. Invalid transitions return 400. |
| AC-3 | `DONE` requires `completionNotes` (≤ 2000 chars). |
| AC-4 | `CANCELLED` requires `completionNotes` explaining cancellation reason. |
| AC-5 | Only the assignee or the creator can change status (403 otherwise). |

---

### US-6.3 · Task Comments

**As the** task assignee or creator,
**I want** to add comments to a task,
**so that** collaboration history is preserved.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/tasks/{taskId}/comments` with `{body}` returns 201 with `{commentId, authorId, body, createdAt}`. |
| AC-2 | GET `/api/tasks/{taskId}/comments` returns a paginated, chronological list. |
| AC-3 | Comments are append-only; no edits or deletes. |
| AC-4 | Only participants (assignee, creator) and ADMIN can view/add comments (403 otherwise). |

---

### US-6.4 · Notification Inbox

**As a** logged-in user,
**I want** an in-app notification inbox,
**so that** I see approval requests, task assignments, and system alerts.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/notifications?read=false&page=0&size=20` returns paginated notifications. |
| AC-2 | Each notification includes `{id, type, title, body, referenceType, referenceId, read, createdAt}`. |
| AC-3 | PATCH `/api/notifications/{notifId}/read` marks a notification as read. |
| AC-4 | GET `/api/notifications/unread-count` returns `{count}`. |
| AC-5 | Notifications are user-scoped — a user can only see their own. |

---

### US-6.5 · Scheduled Reminders

**As the** system,
**I want** to send overdue-task reminders and document-expiry warnings on a schedule,
**so that** stakeholders are reminded without manual follow-up.

| # | Acceptance Criteria |
|---|---|
| AC-1 | A scheduled job runs daily (configurable cron) and identifies overdue tasks (`dueDate < now()` and status ∈ `{OPEN, IN_PROGRESS}`). |
| AC-2 | A reminder notification is sent to the assignee. |
| AC-3 | Reminders are deduplicated: at most one reminder per task per 24-hour period. |
| AC-4 | Document-expiry warnings (30-day and 7-day) are sent to owners and department KMs. |
| AC-5 | Deduplication: at most one warning per threshold per document. |

---

### US-6.6 · Notification Preferences

**As a** logged-in user,
**I want** to configure which notification types I receive,
**so that** I am not overwhelmed by irrelevant alerts.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/users/me/notification-preferences` returns `{type: boolean}` map for each notification type. |
| AC-2 | PUT `/api/users/me/notification-preferences` updates preferences. |
| AC-3 | Default: all types enabled. |
| AC-4 | Preferences are respected by the notification dispatch logic; opted-out types are not delivered. |
| AC-5 | System-critical notifications (e.g., security alerts) cannot be disabled. |

---

## 7. AI Copilot (RAG)

### US-7.1 · Ask the Copilot a Question

**As an** EMPLOYEE (or any authenticated user),
**I want** to ask a natural-language question and get an answer grounded in documents I can access,
**so that** I get trustworthy, cited answers from the knowledge base.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/copilot/conversations/{convId}/messages` with `{question}` returns 200 with `{answer, citations[], retrievedDocumentCount}`. |
| AC-2 | The answer is generated by `ChatModelGateway` using a RAG pipeline: embed question → retrieve top-K chunks (ACL-filtered) → augment prompt → generate. |
| AC-3 | Each citation includes `{documentId, documentTitle, versionId, versionNumber, pageNumber, snippetPreview}`. |
| AC-4 | Only chunks from documents the requesting user can access (evaluated via `DocumentAccessPolicy` in the retrieval SQL) are included in the context. |
| AC-5 | If no relevant chunks are found, the answer is an explicit abstention: _"I don't have enough information in the documents you can access to answer this question."_ |
| AC-6 | If evidence is contradictory, the answer notes the contradiction and cites both sources. |
| AC-7 | Retrieved document text is treated as untrusted data — it cannot grant permissions, invoke tools, or override system instructions. |

---

### US-7.2 · Provider Timeout / Error Handling

**As a** user asking the copilot,
**I want** graceful error handling when the AI provider is slow or down,
**so that** I can retry without losing my question.

| # | Acceptance Criteria |
|---|---|
| AC-1 | If the AI provider times out (configurable, default 30 s), the endpoint returns 503 with `{code: "AI_PROVIDER_TIMEOUT", message: "...", correlationId}`. |
| AC-2 | The user's question is preserved in the conversation so they can see it on reload. |
| AC-3 | The answer field is `null` for failed turns; the UI shows a retry button. |
| AC-4 | Transient errors are retried once internally before returning 503. |

---

### US-7.3 · Restricted Document Existence Never Revealed

**As the** system,
**I want** to ensure the copilot never hints at the existence of documents the user cannot access,
**so that** confidential information is not leaked through inference.

| # | Acceptance Criteria |
|---|---|
| AC-1 | Retrieval queries JOIN against permissions — unauthorized chunks are never fetched from the DB. |
| AC-2 | The system prompt instructs the model: _"Answer only from the provided context. Do not speculate about documents that may exist but were not provided."_ |
| AC-3 | The answer and citations reference only accessible documents. |
| AC-4 | If a user asks about a topic covered only by a restricted document they cannot access, the copilot abstains rather than revealing that relevant information exists. |

---

## 8. Authorized Tool Calling

### US-8.1 · Read-Only Tools for the Copilot

**As an** EMPLOYEE using the copilot,
**I want** the copilot to look up my pending approvals, task statuses, and document metadata on my behalf,
**so that** I get actionable answers without leaving the chat.

| # | Acceptance Criteria |
|---|---|
| AC-1 | The copilot can invoke predefined tools: `get_my_pending_approvals`, `get_task_status`, `search_accessible_documents`, `get_document_metadata`. |
| AC-2 | Each tool has a typed JSON schema for arguments and return values. |
| AC-3 | Tool execution uses the **requesting user's** identity from `SecurityContext` — never a user ID from the model's output or the conversation. |
| AC-4 | Arguments are validated against the schema before execution; invalid arguments result in a tool error message to the model (not an exception to the user). |
| AC-5 | Each tool invocation is recorded in the audit log with `userId, toolName, arguments (sanitized), durationMs`. |
| AC-6 | Per-user quota: max 20 tool invocations per conversation turn; exceeding returns a message to the model: _"Tool call limit reached."_ |

---

### US-8.2 · Tool Timeout and Safety

**As the** system,
**I want** tool calls to be bounded and safe,
**so that** a slow or misbehaving tool does not hang the copilot response.

| # | Acceptance Criteria |
|---|---|
| AC-1 | Each tool call has a timeout (configurable, default 5 s). On timeout, the model receives a tool error: _"Tool timed out."_ |
| AC-2 | No write/mutation tools are registered in v1. The tool registry rejects registration of tools with side effects. |
| AC-3 | Tool results are sanitized before inclusion in the model context (no raw SQL errors, stack traces, or internal paths). |

---

## 9. Conversation History & Feedback

### US-9.1 · Conversation Management

**As a** logged-in user,
**I want** to create, list, and view my copilot conversations,
**so that** I can revisit past Q&A threads.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/copilot/conversations` with `{title?}` creates a new conversation; returns 201 with `{conversationId, title, createdAt}`. |
| AC-2 | GET `/api/copilot/conversations?page=0&size=20` returns the current user's conversations, ordered by `updatedAt` desc. |
| AC-3 | GET `/api/copilot/conversations/{convId}` returns conversation metadata plus paginated messages. |
| AC-4 | A user can only access their own conversations; accessing another user's conversation returns 404. |
| AC-5 | ADMIN cannot read other users' conversations by default (requires explicit audit-justified endpoint, not in v1). |

---

### US-9.2 · Multi-Turn Context

**As a** user in a conversation,
**I want** the copilot to consider previous turns in the conversation when answering,
**so that** I can ask follow-up questions naturally.

| # | Acceptance Criteria |
|---|---|
| AC-1 | Each turn's retrieval is independent — the system re-embeds the latest question (possibly considering conversation context) and retrieves fresh chunks with current ACL checks. |
| AC-2 | The model receives a sliding window of previous turns (configurable, default last 10 turns) as conversation history. |
| AC-3 | If a user's permissions change between turns, the next turn's retrieval reflects the new permissions. |

---

### US-9.3 · Feedback on Copilot Answers

**As a** user,
**I want** to give thumbs-up/down feedback on copilot answers with a reason,
**so that** the system can track answer quality.

| # | Acceptance Criteria |
|---|---|
| AC-1 | POST `/api/copilot/conversations/{convId}/messages/{msgId}/feedback` with `{rating: UP|DOWN, reasonCode?, freeText?}` returns 201. |
| AC-2 | Reason codes (for DOWN): `INCORRECT`, `INCOMPLETE`, `UNSUPPORTED_CITATION`, `OUTDATED_SOURCE`, `PERMISSION_ISSUE`. |
| AC-3 | A user can submit feedback only once per message; duplicate returns 409. |
| AC-4 | Feedback is stored and available via admin reporting endpoints. |

---

### US-9.4 · Retention & Purge

**As an** ADMIN,
**I want** conversation data to be purged after a configurable retention period,
**so that** storage is managed and data-retention policies are met.

| # | Acceptance Criteria |
|---|---|
| AC-1 | A configurable property `knowflow.copilot.retention-days` (default 365) controls the retention window. |
| AC-2 | A scheduled job runs weekly and hard-deletes conversations (and their messages, citations, feedback) older than the retention window. |
| AC-3 | An `AUDIT_CONVERSATIONS_PURGED` event is recorded with the count of deleted conversations. |
| AC-4 | Users can delete their own conversations at any time via DELETE `/api/copilot/conversations/{convId}` (soft-delete; purge job cleans up). |

---

## 10. Administration, Audit & Reporting

### US-10.1 · Audit Log

**As an** ADMIN,
**I want** an immutable audit log of all security-relevant actions,
**so that** I can investigate incidents and demonstrate compliance.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/admin/audit-logs?eventType=...&userId=...&startDate=...&endDate=...&page=0&size=50` returns paginated audit entries. |
| AC-2 | Each entry includes `{id, eventType, actorId, actorEmail, targetType, targetId, details (JSON), ipAddress, timestamp}`. |
| AC-3 | Logged event types include (non-exhaustive): `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `USER_CREATED`, `USER_DEACTIVATED`, `ROLES_CHANGED`, `DOCUMENT_UPLOADED`, `DOCUMENT_DOWNLOADED`, `DOCUMENT_METADATA_UPDATED`, `DOCUMENT_ARCHIVED`, `DOCUMENT_RESTORED`, `PERMISSION_CHANGED`, `REVIEW_SUBMITTED`, `REVIEW_DECIDED`, `TASK_CREATED`, `TASK_STATUS_CHANGED`, `TOOL_EXECUTED`, `CONVERSATIONS_PURGED`, `INGESTION_FAILED`, `DEPARTMENT_CHANGED`, `CATEGORY_CHANGED`. |
| AC-4 | Audit records are append-only at the application layer — no UPDATE or DELETE operations exist on the audit table. |
| AC-5 | Only ADMIN can query the audit log (403 for others). |

---

### US-10.2 · Admin Dashboard Metrics

**As an** ADMIN,
**I want** a dashboard showing operational metrics,
**so that** I can monitor the health of the knowledge base.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/admin/dashboard` returns a DTO with current counts and summaries. |
| AC-2 | Metrics include: total documents (by status), pending approvals, overdue tasks, documents expiring within 30 days, ingestion failures in the last 7 days, active users in the last 30 days, copilot questions in the last 7 days. |
| AC-3 | Response is cacheable (short TTL, e.g., 5 minutes). |
| AC-4 | Only ADMIN can access (403 for others). |

---

### US-10.3 · Reprocess Failed Ingestion Jobs

**As an** ADMIN,
**I want** to retry failed ingestion jobs,
**so that** transient failures don't permanently block document indexing.

| # | Acceptance Criteria |
|---|---|
| AC-1 | GET `/api/admin/ingestion-jobs?status=FAILED&page=0&size=20` lists failed jobs with `{jobId, documentId, versionId, failedAt, errorMessage, retryCount}`. |
| AC-2 | POST `/api/admin/ingestion-jobs/{jobId}/retry` re-enqueues the job; returns 202. |
| AC-3 | POST `/api/admin/ingestion-jobs/retry-all-failed` re-enqueues all failed jobs; returns 202 with `{count}`. |
| AC-4 | Jobs that fail after 3 retries are marked `PERMANENTLY_FAILED` and excluded from bulk retry (can still be individually retried). |
| AC-5 | An `AUDIT_INGESTION_RETRIED` event is recorded. |

---

### US-10.4 · Admin Endpoints for Categories & Departments

**As an** ADMIN,
**I want** CRUD endpoints for organizational entities,
**so that** the system reflects the current organizational structure.

| # | Acceptance Criteria |
|---|---|
| AC-1 | Categories: see US-2.7. |
| AC-2 | Departments: see US-1.7. |
| AC-3 | All admin endpoints require ADMIN role (403 otherwise). |
| AC-4 | All admin mutations are audit-logged. |

---

## Cross-Cutting: Error Handling

### US-CC.1 · Consistent Error Responses

**As a** client developer,
**I want** all error responses to follow a consistent shape,
**so that** I can build a single error-handling layer.

| # | Acceptance Criteria |
|---|---|
| AC-1 | All 4xx/5xx responses return `{code: string, message: string, fieldErrors: [{field, message}]?, correlationId: UUID, timestamp: ISO-8601}`. |
| AC-2 | `correlationId` is propagated from the `X-Correlation-Id` request header (or generated if absent). |
| AC-3 | 400 for validation errors (with `fieldErrors`). |
| AC-4 | 401 for unauthenticated. |
| AC-5 | 403 for insufficient role (except where 404 is used to hide existence). |
| AC-6 | 404 for not found AND for resources the user must not know exist. |
| AC-7 | 409 for state conflicts (duplicate review decision, active permission, etc.). |
| AC-8 | 413 for file too large. |
| AC-9 | 429 for rate limiting. |
| AC-10 | 500 for unexpected server errors — message is generic, details are logged server-side only. |
| AC-11 | 503 for AI provider unavailable. |

---

## Cross-Cutting: Ingestion Pipeline

### US-CC.2 · Async Document Ingestion

**As the** system,
**I want** document ingestion (extract → chunk → embed) to run asynchronously,
**so that** uploads return immediately and the pipeline is resilient.

| # | Acceptance Criteria |
|---|---|
| AC-1 | After upload, a job record is created with `status: PENDING`. |
| AC-2 | The pipeline stages are: `PENDING → EXTRACTING → CHUNKING → EMBEDDING → COMPLETED` (or `FAILED` from any stage). |
| AC-3 | Each stage is idempotent — re-running a stage with the same input produces the same output without side effects. |
| AC-4 | On failure, the job is retried with exponential backoff (initial 30 s, max 15 min, max 3 retries). |
| AC-5 | Job progress is observable: GET `/api/documents/{docId}/versions/{versionId}/ingestion-status` returns `{status, stage, errorMessage?, updatedAt}`. |
| AC-6 | During ingestion, the document is still visible in search by metadata (title, description) but marked as _"Content indexing in progress"_. |
