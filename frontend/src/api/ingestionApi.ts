import { Page } from './documentTypes';

export interface AdminJobDto {
  id: string;
  versionId: string;
  pipelineVersion: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRY_SCHEDULED';
  attempts: number;
  maxAttempts: number;
  nextAttemptAt?: string;
  startedAt?: string;
  finishedAt?: string;
  errorCode?: string;
  errorMessage?: string;
  errorType?: 'TRANSIENT' | 'PERMANENT';
  lockedBy?: string;
  lockedUntil?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChunkCitationDto {
  chunkId: string;
  documentId: string;
  documentTitle: string;
  versionId: string;
  versionNumber: number;
  pageStart: number;
  pageEnd: number;
  snippet: string;
  relevanceScore: number;
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const ingestionApi = {
  async listJobs(status?: string, versionId?: string, page = 0, size = 20): Promise<Page<AdminJobDto>> {
    let url = `/api/v1/admin/ingestion/jobs?page=${page}&size=${size}`;
    if (status && status !== 'ALL') url += `&status=${encodeURIComponent(status)}`;
    if (versionId) url += `&versionId=${encodeURIComponent(versionId)}`;

    const res = await fetch(url, { headers: getAuthHeaders() });
    if (!res.ok) throw new Error('Failed to load ingestion jobs');
    return res.json();
  },

  async retryJob(id: string, reason: string): Promise<AdminJobDto> {
    const res = await fetch(`/api/v1/admin/ingestion/jobs/${id}/retry`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ reason }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to retry ingestion job');
    }
    return res.json();
  },

  async reprocessDocument(documentId: string, pipelineVersion: string, reason: string): Promise<AdminJobDto> {
    const res = await fetch(`/api/v1/admin/ingestion/documents/${documentId}/reprocess`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ pipelineVersion, reason }),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Failed to trigger reprocessing');
    }
    return res.json();
  },

  async searchChunks(query: string, limit = 10): Promise<ChunkCitationDto[]> {
    const res = await fetch(`/api/v1/search/chunks?q=${encodeURIComponent(query)}&limit=${limit}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to search document chunks');
    return res.json();
  }
};
