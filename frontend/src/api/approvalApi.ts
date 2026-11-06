export interface ApprovalHistoryDto {
  id: string;
  actorId: string;
  action: string;
  comment?: string;
  fromState: string;
  toState: string;
  createdAt: string;
}

export interface ApprovalRequestDto {
  id: string;
  documentVersionId: string;
  submittedBy: string;
  approverId: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
  submittedAt: string;
  completedAt?: string;
  version: number;
  history: ApprovalHistoryDto[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Authorization': token ? `Bearer ${token}` : '',
    'Content-Type': 'application/json',
  };
}

export const approvalApi = {
  async listAssigned(page = 0, size = 20): Promise<Page<ApprovalRequestDto>> {
    const res = await fetch(`/api/v1/approvals/assigned?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load assigned approvals');
    return res.json();
  },

  async listSubmitted(page = 0, size = 20): Promise<Page<ApprovalRequestDto>> {
    const res = await fetch(`/api/v1/approvals/submitted?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load submitted approvals');
    return res.json();
  },

  async submitReview(documentId: string, approverId: string, versionNumber?: number): Promise<ApprovalRequestDto> {
    const res = await fetch(`/api/v1/documents/${documentId}/submit-review`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ approverId, versionNumber }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to submit document for review');
    }
    return res.json();
  },

  async approve(approvalId: string, comment?: string): Promise<ApprovalRequestDto> {
    const res = await fetch(`/api/v1/approvals/${approvalId}/approve`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ comment }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Approval action failed');
    }
    return res.json();
  },

  async reject(approvalId: string, comment: string): Promise<ApprovalRequestDto> {
    const res = await fetch(`/api/v1/approvals/${approvalId}/reject`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ comment }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Rejection action failed');
    }
    return res.json();
  },

  async requestChanges(approvalId: string, comment: string): Promise<ApprovalRequestDto> {
    const res = await fetch(`/api/v1/approvals/${approvalId}/request-changes`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ comment }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Change request action failed');
    }
    return res.json();
  }
};
