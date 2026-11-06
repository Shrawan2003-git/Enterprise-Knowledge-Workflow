-- V6__workflow_approvals.sql: Approval Requests and Immutable History

CREATE TABLE IF NOT EXISTS approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES users(id),
    approver_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED'))
);

CREATE INDEX IF NOT EXISTS idx_approvals_approver_status ON approval_requests(approver_id, status);
CREATE INDEX IF NOT EXISTS idx_approvals_version ON approval_requests(document_version_id);
CREATE INDEX IF NOT EXISTS idx_approvals_submitter ON approval_requests(submitted_by);

CREATE TABLE IF NOT EXISTS approval_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    comment TEXT,
    from_state VARCHAR(30) NOT NULL,
    to_state VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_approval_history_request ON approval_history(request_id, created_at);
