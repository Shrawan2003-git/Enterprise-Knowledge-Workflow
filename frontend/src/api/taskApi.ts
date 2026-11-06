export interface TaskCommentDto {
  id: string;
  authorId: string;
  content: string;
  createdAt: string;
}

export interface TaskDto {
  id: string;
  title: string;
  description?: string;
  creatorId: string;
  assigneeId?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
  dueAt?: string;
  documentId?: string;
  approvalRequestId?: string;
  version: number;
  createdAt: string;
  comments: TaskCommentDto[];
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  assigneeId?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  dueAt?: string;
  documentId?: string;
  approvalRequestId?: string;
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

export const taskApi = {
  async listMyTasks(page = 0, size = 50): Promise<Page<TaskDto>> {
    const res = await fetch(`/api/v1/tasks?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load tasks');
    return res.json();
  },

  async createTask(req: CreateTaskRequest): Promise<TaskDto> {
    const res = await fetch('/api/v1/tasks', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(req),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to create task');
    }
    return res.json();
  },

  async updateStatus(id: string, status: string, version: number): Promise<TaskDto> {
    const res = await fetch(`/api/v1/tasks/${id}/status`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
      body: JSON.stringify({ status, version }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to update task status');
    }
    return res.json();
  },

  async addComment(id: string, content: string): Promise<void> {
    const res = await fetch(`/api/v1/tasks/${id}/comments`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ content }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to add comment');
    }
  }
};
