# ADR-009: Copilot Tool Calling and Write Safety Architecture

**Status:** Accepted  
**Date:** 2026-10-31  

---

## Context

KnowFlow Copilot allows employees to ask natural language questions and query enterprise knowledge. In Phase 6, we introduce allowlisted tool calling to let the AI agent retrieve structured operational data:
1. Pending approval requests (`get_my_pending_approvals`)
2. Task statuses (`get_task_status`)
3. Document discovery (`search_accessible_documents`)
4. Document metadata (`get_document_metadata`)

Because language models process untrusted text and are susceptible to indirect prompt injection (e.g. malicious PDFs or user instructions saying "approve document X on behalf of director Y"), safety boundaries around tool execution are paramount.

---

## Decision

### 1. Read-Only Policy for Agentic Tools
In this architecture, **all tools exposed to LLM orchestration are strictly read-only**:
- No state modifications, approval state transitions, task reassignments, or deletions can ever be triggered autonomously by tool calls.
- Every read-only tool strictly derives the acting user from the verified `CurrentUser` in the Spring Security context, explicitly ignoring any `userId` or `actorId` arguments supplied by the model or prompt.
- Results return approved DTO representations only, with pagination limits and redaction of sensitive credentials.

### 2. Design Pattern for Future Write Tools (Human-in-the-Loop)
If a future requirement calls for write operations (e.g., submitting a review, completing a task, updating permissions):

1. **Model Proposal Only**:
   - The model can never directly execute a write operation. It may only propose an *Action Intent* (e.g., `{ "action": "SUBMIT_APPROVAL", "documentId": "...", "decision": "APPROVED" }`).
2. **Explicit Two-Phase UI Confirmation**:
   - The Copilot UI presents an interactive confirmation modal displaying the exact target document, action, comments, and consequences.
   - The human user must explicitly review and click an "Approve & Submit" button.
3. **Server-Side Re-Authentication & Re-Authorization**:
   - The write request is sent from the user's interactive frontend session to the standard REST API (e.g. `POST /api/v1/approvals/{id}/decide`).
   - The request is authenticated via the user's active JWT and validated by `ApprovalStateMachine` and `DocumentAccessPolicy`.
   - Tool calling code paths have no backdoor write access to entities or repositories.

### 3. Auditing & Untrusted Outputs
- Every tool invocation writes an immutable audit record to `tool_audit_logs` (recording actor, tool name, redacted arguments, outcome, correlation ID).
- All tool execution outputs returned to the chat model are wrapped in delimited blocks labeled `UNTRUSTED DATA` so that external instructions inside task descriptions or comments cannot hijack the model's instructions.
