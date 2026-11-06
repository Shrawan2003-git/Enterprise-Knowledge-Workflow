# KnowFlow — REST API Specification

> **Version:** 1.0  
> **Base URL:** `/api/v1`  
> **Auth:** Bearer JWT in `Authorization` header

---

## Conventions

### Request / Response Format
- All request/response bodies are `application/json` unless noted (multipart for uploads)
- All timestamps are ISO 8601 in UTC (`2026-10-29T12:00:00Z`)
- All IDs are UUIDs

### Pagination
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8,
  "first": true,
  "last": false
}
```
- Default `size`: 20, max `size`: 100
- `page` is zero-based
- `totalElements` is ACL-filtered (never reveals restricted counts)

### Error Shape
Every error response uses this consistent structure:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "One or more fields are invalid",
  "fieldErrors": [
    { "field": "email", "message": "must be a valid email address" }
  ],
  "correlationId": "abc-123-def",
  "timestamp": "2026-10-29T12:00:00Z"
}
```

### Status Codes
| Code | Usage |
|------|-------|
| 200 | Success (GET, PATCH) |
| 201 | Created (POST) |
| 204 | No Content (DELETE, logout) |
| 400 | Invalid input / validation error |
| 401 | Unauthenticated (missing/invalid/expired token) |
| 403 | Forbidden (authenticated but insufficient role/permission) |
| 404 | Not found OR user must not learn resource exists |
| 409 | Conflict (optimistic lock, duplicate decision, state conflict) |
| 413 | Payload too large (upload exceeds size limit) |
| 429 | Rate limit exceeded |
| 500 | Internal server error |
| 503 | Service unavailable (AI provider down) |

### Headers
| Header | Direction | Purpose |
|--------|-----------|---------|
| `Authorization: Bearer <token>` | Request | JWT access token |
| `X-Correlation-Id` | Response | Request correlation for debugging |
| `X-Content-Type-Options: nosniff` | Response | Prevent MIME sniffing on downloads |
| `X-Idempotency-Key` | Request | Optional, for retry-prone writes |

---

## Endpoints

### 1. Authentication

#### `POST /auth/login`
Authenticate with email and password. Rate-limited: 5 attempts / email / 15 min.

**Roles:** Public (unauthenticated)

**Request:**
```json
{
  "email": "john@company.com",
  "password": "s3cur3Pa$$"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "dGhpcyBpcyBh...",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "john@company.com",
    "displayName": "John Smith",
    "roles": ["EMPLOYEE"],
    "departmentId": "uuid",
    "departmentName": "Engineering"
  }
}
```

**Errors:** 401 (generic "Invalid credentials" — never reveal which field is wrong), 429 (rate limited)

---

#### `POST /auth/refresh`
Rotate refresh token. Old refresh token becomes unusable.

**Roles:** Authenticated (via refresh token)

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBh..."
}
```

**Response (200):** Same shape as login response with new tokens.

**Errors:** 401 (invalid/expired/revoked refresh token)

---

#### `POST /auth/logout`
Revoke the current refresh token.

**Roles:** Authenticated

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBh..."
}
```

**Response:** 204 No Content

---

### 2. Users & Profile

#### `GET /users/me`
Get current user's profile. Identity from SecurityContext only.

**Roles:** Any authenticated user

**Response (200):**
```json
{
  "id": "uuid",
  "email": "john@company.com",
  "displayName": "John Smith",
  "status": "ACTIVE",
  "roles": ["EMPLOYEE"],
  "departmentId": "uuid",
  "departmentName": "Engineering",
  "createdAt": "2026-10-29T12:00:00Z"
}
```

---

#### `GET /users` — Admin only
List all users with pagination, optional filters: `status`, `departmentId`, `role`.

**Roles:** ADMIN

**Response:** Paginated list of `UserSummaryDto`.

---

#### `POST /users` — Admin only
Create a new user.

**Roles:** ADMIN

**Request:**
```json
{
  "email": "jane@company.com",
  "displayName": "Jane Doe",
  "password": "initialP@ss1",
  "departmentId": "uuid",
  "roleNames": ["EMPLOYEE"]
}
```

**Response (201):** `UserDto` (no password in response)

---

#### `PATCH /users/{id}/status` — Admin only
Activate or deactivate a user.

**Roles:** ADMIN

**Request:**
```json
{ "status": "INACTIVE" }
```

**Response (200):** Updated `UserDto`. **Audited.**

---

#### `PUT /users/{id}/roles` — Admin only
Replace user's role set.

**Roles:** ADMIN

**Request:**
```json
{ "roleNames": ["EMPLOYEE", "APPROVER"] }
```

**Response (200):** Updated `UserDto`. **Audited.**

---

### 3. Departments

#### `GET /departments`
List all departments.

**Roles:** Any authenticated user

**Response:** List of `DepartmentDto`

---

#### `POST /departments` — Admin only
Create a department.

**Roles:** ADMIN

**Request:**
```json
{ "name": "Engineering", "description": "Software Engineering" }
```

**Response (201):** `DepartmentDto`

---

#### `PUT /departments/{id}` — Admin only
Update department.

**Roles:** ADMIN

---

#### `PATCH /users/{id}/department` — Admin only
Change a user's department.

**Roles:** ADMIN

**Request:**
```json
{ "departmentId": "uuid" }
```

**Response (200):** Updated `UserDto`. **Audited.**

---

### 4. Documents

#### `GET /documents`
List/search accessible documents. ACL-filtered in SQL before pagination.

**Roles:** Any authenticated user

**Query params:**
| Param | Type | Description |
|-------|------|-------------|
| `q` | String | Full-text search query |
| `category` | UUID | Category filter |
| `tag` | String | Tag name filter |
| `department` | UUID | Department filter |
| `status` | String | Document status filter |
| `owner` | UUID | Owner filter |
| `classification` | String | Classification filter |
| `from` | ISO date | Created after |
| `to` | ISO date | Created before |
| `sort` | String | Sort field (`title`, `createdAt`, `updatedAt`, `relevance`) |
| `direction` | String | `asc` or `desc` |
| `page` | Integer | Page number (0-based) |
| `size` | Integer | Page size (max 100) |

**Response (200):**
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Safety Procedure Manual",
      "description": "Standard safety procedures for...",
      "status": "APPROVED",
      "classification": "INTERNAL",
      "categoryName": "Policies",
      "tags": ["safety", "mandatory"],
      "ownerName": "Jane Doe",
      "departmentName": "Operations",
      "currentVersionNumber": 2,
      "expiryAt": "2027-06-01T00:00:00Z",
      "snippet": "...required <b>safety checks</b> before...",
      "createdAt": "2026-10-01T10:00:00Z",
      "updatedAt": "2026-10-15T14:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

---

#### `POST /documents`
Create a document and upload the first version. Multipart request.

**Roles:** KNOWLEDGE_MANAGER, ADMIN

**Request (multipart/form-data):**
| Part | Type | Description |
|------|------|-------------|
| `file` | Binary | The document file (PDF, max 50MB) |
| `metadata` | JSON | Document metadata (see below) |

**Metadata JSON:**
```json
{
  "title": "Safety Procedure Manual",
  "description": "Standard safety procedures for plant operations",
  "departmentId": "uuid",
  "categoryId": "uuid",
  "classification": "INTERNAL",
  "tags": ["safety", "mandatory"],
  "expiryAt": "2027-06-01T00:00:00Z",
  "changeNote": "Initial upload"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "title": "Safety Procedure Manual",
  "status": "DRAFT",
  "classification": "INTERNAL",
  "currentVersionNumber": 1,
  "currentVersion": {
    "id": "uuid",
    "versionNumber": 1,
    "checksum": "sha256:abc123...",
    "mimeType": "application/pdf",
    "sizeBytes": 1048576,
    "processingStatus": "PENDING",
    "uploadedBy": "uuid",
    "changeNote": "Initial upload",
    "createdAt": "2026-10-29T12:00:00Z"
  },
  "createdAt": "2026-10-29T12:00:00Z"
}
```

**Errors:** 400 (validation), 413 (file too large), 415 (unsupported type)

---

#### `GET /documents/{id}`
Get document metadata with current version info.

**Roles:** Authorized users (per DocumentAccessPolicy)

**Response (200):** Full `DocumentDetailDto` including metadata, current version, category, tags.

**Errors:** 404 (not found or not authorized — never 403 for existence hiding)

---

#### `PUT /documents/{id}`
Update document metadata (title, description, category, tags, classification, expiry).

**Roles:** Owner, KNOWLEDGE_MANAGER (same dept), ADMIN

**Request:**
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "categoryId": "uuid",
  "classification": "CONFIDENTIAL",
  "tags": ["safety", "updated"],
  "expiryAt": "2027-12-01T00:00:00Z",
  "version": 3
}
```
`version` field is the optimistic lock version — required.

**Errors:** 409 (optimistic lock conflict)

---

#### `GET /documents/{id}/versions`
List all versions of a document.

**Roles:** Authorized users (per DocumentAccessPolicy)

**Response:** List of `VersionSummaryDto`

---

#### `POST /documents/{id}/versions`
Upload a new version (creates a new DRAFT candidate — does NOT affect in-progress approvals of older versions).

**Roles:** Owner, KNOWLEDGE_MANAGER (same dept), ADMIN

**Request (multipart):** `file` + `changeNote`

**Response (201):** `VersionDto`

---

#### `GET /documents/{id}/download`
Download a specific version's file. Authorized on EVERY request.

**Roles:** Authorized users (per DocumentAccessPolicy — VIEW or higher)

**Query params:** `version` (optional — defaults to current)

**Response:** Binary stream with headers:
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="document.pdf"
X-Content-Type-Options: nosniff
```

**Audited** for CONFIDENTIAL and RESTRICTED classification.

---

#### `PUT /documents/{id}/permissions`
Replace the permission set for a document.

**Roles:** Owner (MANAGE level), ADMIN

**Request:**
```json
{
  "permissions": [
    { "principalType": "USER", "principalId": "uuid", "permissionLevel": "VIEW" },
    { "principalType": "DEPARTMENT", "principalId": "uuid", "permissionLevel": "VIEW" },
    { "principalType": "USER", "principalId": "uuid", "permissionLevel": "MANAGE" }
  ]
}
```

**Response (200):** Updated permission list. **Audited** (before/after summary).

---

#### `POST /documents/{id}/archive`
Archive a document (soft-delete).

**Roles:** Owner, KNOWLEDGE_MANAGER (same dept), ADMIN

**Response:** 200 with updated status. **Audited.**

---

#### `POST /documents/{id}/restore`
Restore an archived document.

**Roles:** Owner, ADMIN

**Response:** 200 with updated status. **Audited.**

---

### 5. Categories & Tags

#### `GET /categories`
**Roles:** Any authenticated user

#### `POST /categories` — Admin/KM only
**Request:** `{ "name": "Policies", "description": "..." }`

#### `GET /tags`
**Roles:** Any authenticated user

#### `POST /tags` — Admin/KM only
**Request:** `{ "name": "safety" }` (normalized to lowercase)

---

### 6. Approvals

#### `POST /documents/{id}/submit-review`
Submit a specific version for review.

**Roles:** Owner, KNOWLEDGE_MANAGER

**Request:**
```json
{
  "versionId": "uuid",
  "approverId": "uuid"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "documentVersionId": "uuid",
  "submittedBy": "uuid",
  "approverId": "uuid",
  "status": "PENDING",
  "submittedAt": "2026-10-29T12:00:00Z"
}
```

**Errors:** 400 (version not DRAFT), 409 (already submitted), 403 (self-approval)

---

#### `GET /approvals`
List approvals. Filtered by role: shows assigned approvals for APPROVER, submitted for owner.

**Roles:** Any authenticated user (sees own relevant approvals)

**Query params:** `status`, `page`, `size`

---

#### `GET /approvals/{id}`
Get approval detail with document context and history.

**Roles:** Assigned approver or submitter

---

#### `POST /approvals/{id}/approve`
Approve the review.

**Roles:** Assigned APPROVER only

**Request:**
```json
{ "comment": "Looks good, approved." }
```

**Response (200):** Updated approval with history entry. **Audited.**

**Errors:** 403 (not assigned), 409 (already decided / optimistic lock), 400 (outdated version)

---

#### `POST /approvals/{id}/reject`
Reject the review. Comment required.

**Roles:** Assigned APPROVER only

**Request:**
```json
{ "comment": "Section 3 needs revision — safety checklist incomplete." }
```

**Errors:** 400 (comment required)

---

#### `POST /approvals/{id}/request-changes`
Request changes on the review.

**Roles:** Assigned APPROVER only

**Request:**
```json
{ "comment": "Please update the diagram on page 5." }
```

---

### 7. Tasks

#### `GET /tasks`
List tasks visible to the caller (assigned to me, created by me).

**Roles:** Any authenticated user

**Query params:** `status`, `priority`, `assignee`, `linkedDocumentId`, `overdue` (boolean), `page`, `size`, `sort`

---

#### `POST /tasks`
Create a task.

**Roles:** Any authenticated user

**Request:**
```json
{
  "title": "Review updated safety procedure",
  "description": "Check section 3 revisions",
  "assigneeId": "uuid",
  "linkedDocumentId": "uuid",
  "linkedApprovalId": "uuid",
  "priority": "HIGH",
  "dueAt": "2026-11-15T00:00:00Z"
}
```

**Validation:** Creator must have access to linked document/approval.

---

#### `GET /tasks/{id}`
Get task detail.

**Roles:** Assignee, creator, ADMIN

---

#### `PATCH /tasks/{id}/status`
Update task status with validated transitions.

**Roles:** Assignee, creator (for CANCELLED)

**Request:**
```json
{
  "status": "IN_PROGRESS",
  "completionNote": null,
  "version": 1
}
```

Valid transitions: OPEN → IN_PROGRESS, IN_PROGRESS → DONE/CANCELLED, OPEN → CANCELLED

---

#### `POST /tasks/{id}/comments`
Add a comment to a task.

**Roles:** Assignee, creator

**Request:**
```json
{ "content": "Started working on this." }
```

---

### 8. Notifications

#### `GET /notifications`
List current user's notifications with pagination.

**Roles:** Any authenticated user (own notifications only)

**Query params:** `read` (boolean), `page`, `size`

---

#### `GET /notifications/unread-count`
Get unread notification count.

**Roles:** Any authenticated user

**Response:**
```json
{ "count": 5 }
```

---

#### `POST /notifications/{id}/mark-read`
Mark a notification as read.

**Roles:** Owner only

---

#### `POST /notifications/mark-all-read`
Mark all notifications as read.

**Roles:** Any authenticated user (own only)

---

### 9. AI Copilot

#### `POST /copilot/conversations`
Create a new conversation.

**Roles:** Any authenticated user

**Request:**
```json
{ "title": "Safety procedure questions" }
```

**Response (201):** `ConversationDto` with `id`, `title`, `createdAt`

---

#### `GET /copilot/conversations`
List my conversations.

**Roles:** Any authenticated user (own only)

---

#### `GET /copilot/conversations/{id}`
Get conversation with messages and citations.

**Roles:** Owner only (returns 404 for non-owners)

---

#### `POST /copilot/conversations/{id}/messages`
Ask a question. Triggers the full RAG pipeline with ACL-filtered retrieval.

**Roles:** Owner only

**Request:**
```json
{ "content": "What safety checks are required before starting the procedure?" }
```

**Response (200):** (or SSE stream if `Accept: text/event-stream`)
```json
{
  "id": "uuid",
  "role": "ASSISTANT",
  "content": "According to the Safety Procedure Manual (v2), the following checks are required before starting the procedure:\n\n1. **Equipment inspection** — verify all safety equipment is functional [1]\n2. **Personnel clearance** — confirm all personnel have completed training [1]\n3. **Environmental check** — assess weather and environmental conditions [2]\n\nThese checks must be completed and documented before any work begins.",
  "confidence": "ANSWERED",
  "citations": [
    {
      "id": "uuid",
      "sourceId": "S1",
      "documentTitle": "Safety Procedure Manual",
      "versionNumber": 2,
      "pageStart": 3,
      "pageEnd": 4,
      "snippet": "Before starting any procedure, the following safety checks must be completed..."
    },
    {
      "id": "uuid",
      "sourceId": "S2",
      "documentTitle": "Safety Procedure Manual",
      "versionNumber": 2,
      "pageStart": 7,
      "pageEnd": 7,
      "snippet": "Environmental conditions must be assessed including..."
    }
  ],
  "metadata": {
    "model": "gpt-4o",
    "promptVersion": "v1.2",
    "retrievalMs": 145,
    "generationMs": 2340,
    "tokenCount": 512
  },
  "createdAt": "2026-10-29T12:00:05Z"
}
```

**Confidence values:**
| Value | Meaning |
|-------|---------|
| `ANSWERED` | Sufficient evidence found, answer grounded in citations |
| `INSUFFICIENT` | Not enough evidence to answer confidently |
| `CONFLICT` | Conflicting information found in sources — both shown |

**Errors:** 429 (rate limit — 30/user/hour), 503 (AI provider unavailable)

---

#### `POST /copilot/messages/{id}/feedback`
Submit feedback on an answer.

**Roles:** Message owner only

**Request:**
```json
{
  "rating": "THUMBS_DOWN",
  "reasonCode": "INCOMPLETE",
  "comment": "Missing the environmental checklist details"
}
```

---

#### `DELETE /copilot/conversations/{id}`
Delete a conversation.

**Roles:** Owner only

---

### 10. Administration & Reporting

#### `GET /reports/overview`
Operational dashboard metrics (role/department-scoped).

**Roles:** KNOWLEDGE_MANAGER, ADMIN

**Response (200):**
```json
{
  "documents": {
    "total": 156,
    "byStatus": { "DRAFT": 12, "APPROVED": 130, "ARCHIVED": 14 },
    "expiringWithin30Days": 5
  },
  "approvals": {
    "pending": 8,
    "completedThisWeek": 15
  },
  "tasks": {
    "open": 23,
    "overdue": 4,
    "completedThisWeek": 12
  },
  "ingestion": {
    "pending": 2,
    "failed": 1,
    "queueDepth": 3
  },
  "copilot": {
    "questionsToday": 45,
    "averageRetrievalMs": 120,
    "averageGenerationMs": 2100
  }
}
```

---

#### `GET /audit-logs`
Search audit events.

**Roles:** ADMIN only

**Query params:** `actorId`, `action`, `entityType`, `entityId`, `from`, `to`, `page`, `size`

**Response:** Paginated list of `AuditLogDto` (sensitive metadata redacted)

---

#### `GET /admin/ingestion-jobs`
List ingestion jobs with filters.

**Roles:** ADMIN, KNOWLEDGE_MANAGER

**Query params:** `status`, `versionId`, `page`, `size`

---

#### `POST /admin/ingestion-jobs/{id}/retry`
Retry a failed ingestion job. Requires reason.

**Roles:** ADMIN

**Request:**
```json
{ "reason": "Provider was temporarily down, retrying" }
```

**Audited** with actor + reason.

---

### 11. Health & System

#### `GET /actuator/health`
Health check. Readiness depends on DB + storage only, NOT AI provider.

**Roles:** Public

#### `GET /swagger-ui`
OpenAPI documentation. Local profile only.

---

## DTO Summary

| DTO | Used By | Key Fields |
|-----|---------|------------|
| `LoginRequest` | POST /auth/login | email, password |
| `AuthResponse` | Login/refresh | accessToken, refreshToken, expiresIn, user |
| `UserDto` | Profile, admin | id, email, displayName, status, roles, departmentId |
| `UserSummaryDto` | Lists | id, email, displayName, status |
| `DepartmentDto` | Department CRUD | id, name, description |
| `DocumentSummaryDto` | Document list | id, title, status, classification, category, tags, owner, snippet |
| `DocumentDetailDto` | Document detail | Full metadata + currentVersion + category + tags |
| `VersionDto` | Version detail | id, versionNumber, checksum, mimeType, sizeBytes, processingStatus |
| `VersionSummaryDto` | Version list | id, versionNumber, uploadedBy, createdAt, processingStatus |
| `DocumentPermissionDto` | Permission CRUD | principalType, principalId, permissionLevel |
| `ApprovalRequestDto` | Approval detail | id, documentVersionId, submittedBy, approverId, status, history |
| `ApprovalHistoryDto` | Approval history | actorId, action, comment, fromState, toState, createdAt |
| `TaskDto` | Task detail | id, title, assigneeId, priority, status, dueAt, linkedDoc |
| `TaskCommentDto` | Task comments | id, authorId, content, createdAt |
| `NotificationDto` | Notification list | id, type, title, message, readAt, createdAt |
| `ConversationDto` | Conversation list | id, title, createdAt, messageCount |
| `MessageDto` | Chat message | id, role, content, confidence, citations, metadata |
| `CitationDto` | Citation detail | documentTitle, versionNumber, pageStart, pageEnd, snippet |
| `FeedbackDto` | Feedback submit | rating, reasonCode, comment |
| `ReportOverviewDto` | Dashboard | documents, approvals, tasks, ingestion, copilot metrics |
| `AuditLogDto` | Audit viewer | actorId, action, entityType, entityId, timestamp, detail |
| `ApiError` | All errors | code, message, fieldErrors, correlationId, timestamp |
