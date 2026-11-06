export type ConfidenceLevel = 'ANSWERED' | 'INSUFFICIENT' | 'CONFLICT';

export interface CopilotCitation {
  sourceId: string;
  documentId: string;
  documentTitle: string;
  versionNumber: number;
  pageStart: number | null;
  pageEnd: number | null;
  pageHint: string;
  snippet: string;
}

export interface CopilotAnswer {
  answer: string;
  citations: CopilotCitation[];
  confidence: ConfidenceLevel;
  model: string;
  promptVersion: string;
  retrievalMs: number;
  generationMs: number;
  tokenUsage: number | null;
  isRecoverableError: boolean;
  disclaimer: string;
}

export interface CopilotAskRequest {
  question: string;
  topK?: number;
  minScore?: number;
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const copilotApi = {
  async ask(req: CopilotAskRequest): Promise<CopilotAnswer> {
    const res = await fetch('/api/v1/copilot/ask', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(req),
    });

    if (!res.ok) {
      if (res.status === 429) {
        throw new Error('Rate limit exceeded (30 requests/hour). Please wait a moment before trying again.');
      }
      if (res.status === 401) {
        throw new Error('Session expired or unauthenticated. Please log in again.');
      }
      const data = await res.json().catch(() => null);
      throw new Error(data?.message || `Copilot request failed with status ${res.status}`);
    }

    return res.json();
  },
};
