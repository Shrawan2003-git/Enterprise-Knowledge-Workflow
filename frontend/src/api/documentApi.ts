import { DocumentDetailDto, DocumentSummaryDto, VersionDto, Page } from './documentTypes';

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const documentApi = {
  async listDocuments(page = 0, size = 20): Promise<Page<DocumentSummaryDto>> {
    const res = await fetch(`/api/v1/documents?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load documents');
    return res.json();
  },

  async getDocument(id: string): Promise<DocumentDetailDto> {
    const res = await fetch(`/api/v1/documents/${id}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Document not found or access denied');
    return res.json();
  },

  async uploadDocument(file: File, metadata: any): Promise<DocumentDetailDto> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('metadata', JSON.stringify(metadata));

    const token = localStorage.getItem('knowflow_access_token');
    const res = await fetch('/api/v1/documents', {
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : '',
      },
      body: formData,
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Upload failed');
    }
    return res.json();
  },

  async uploadNewVersion(id: string, file: File, changeNote?: string): Promise<VersionDto> {
    const formData = new FormData();
    formData.append('file', file);
    if (changeNote) formData.append('changeNote', changeNote);

    const token = localStorage.getItem('knowflow_access_token');
    const res = await fetch(`/api/v1/documents/${id}/versions`, {
      method: 'POST',
      headers: {
        'Authorization': token ? `Bearer ${token}` : '',
      },
      body: formData,
    });

    if (!res.ok) throw new Error('Failed to upload new version');
    return res.json();
  },

  async listVersions(id: string): Promise<VersionDto[]> {
    const res = await fetch(`/api/v1/documents/${id}/versions`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load versions');
    return res.json();
  },

  async downloadDocument(id: string, version?: number): Promise<Blob> {
    const url = version ? `/api/v1/documents/${id}/download?version=${version}` : `/api/v1/documents/${id}/download`;
    const res = await fetch(url, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Download failed');
    return res.blob();
  },

  async archiveDocument(id: string): Promise<void> {
    const res = await fetch(`/api/v1/documents/${id}/archive`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to archive document');
  },

  async restoreDocument(id: string): Promise<void> {
    const res = await fetch(`/api/v1/documents/${id}/restore`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to restore document');
  }
};
