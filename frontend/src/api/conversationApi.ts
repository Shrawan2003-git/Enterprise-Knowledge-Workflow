export interface CitationItem {
  id: string;
  sourceId: string;
  documentId: string;
  documentTitle: string;
  versionNumber: number;
  pageStart: number | null;
  pageEnd: number | null;
  pageHint: string;
  snippet: string | null;
  canAccess: boolean;
}

export type FeedbackReasonCode =
  | 'INCORRECT'
  | 'INCOMPLETE'
  | 'UNSUPPORTED_CITATION'
  | 'OUTDATED_SOURCE'
  | 'PERMISSION_ISSUE';

export interface FeedbackItem {
  id: string;
  rating: number; // 1 or -1
  reasonCode: FeedbackReasonCode | null;
  comment: string | null;
  createdAt: string;
}

export interface MessageItem {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant';
  content: string;
  model: string | null;
  promptVersion: string | null;
  confidence: 'ANSWERED' | 'INSUFFICIENT' | 'CONFLICT' | null;
  createdAt: string;
  citations: CitationItem[];
  feedback: FeedbackItem | null;
}

export interface ConversationSummary {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

export interface ConversationDetail {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: MessageItem[];
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const conversationApi = {
  async listConversations(): Promise<ConversationSummary[]> {
    const res = await fetch('/api/v1/copilot/conversations', {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load conversations');
    return res.json();
  },

  async createConversation(title?: string): Promise<ConversationDetail> {
    const res = await fetch('/api/v1/copilot/conversations', {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ title }),
    });
    if (!res.ok) throw new Error('Failed to create conversation');
    return res.json();
  },

  async getConversation(id: string): Promise<ConversationDetail> {
    const res = await fetch(`/api/v1/copilot/conversations/${id}`, {
      headers: getAuthHeaders(),
    });
    if (res.status === 404) {
      throw new Error('NOT_FOUND');
    }
    if (!res.ok) throw new Error('Failed to load conversation');
    return res.json();
  },

  async deleteConversation(id: string): Promise<void> {
    const res = await fetch(`/api/v1/copilot/conversations/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to delete conversation');
  },

  async postMessage(conversationId: string, content: string): Promise<MessageItem> {
    const res = await fetch(`/api/v1/copilot/conversations/${conversationId}/messages`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ content }),
    });
    if (!res.ok) {
      const errData = await res.json().catch(() => null);
      throw new Error(errData?.message || 'Failed to post message to conversation');
    }
    return res.json();
  },

  async submitFeedback(messageId: string, rating: number, reasonCode?: FeedbackReasonCode, comment?: string): Promise<FeedbackItem> {
    const res = await fetch(`/api/v1/copilot/messages/${messageId}/feedback`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ rating, reasonCode, comment }),
    });
    if (!res.ok) throw new Error('Failed to submit feedback');
    return res.json();
  },

  async verifyCitation(citationId: string): Promise<CitationItem> {
    const res = await fetch(`/api/v1/copilot/citations/${citationId}/verify`, {
      headers: getAuthHeaders(),
    });
    if (res.status === 403 || res.status === 404) {
      throw new Error('ACCESS_DENIED');
    }
    if (!res.ok) throw new Error('Failed to verify citation access');
    return res.json();
  },
};
