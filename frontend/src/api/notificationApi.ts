export interface NotificationDto {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  linkUrl?: string;
  readAt?: string;
  createdAt: string;
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
  };
}

export const notificationApi = {
  async getMyNotifications(page = 0, size = 20): Promise<Page<NotificationDto>> {
    const res = await fetch(`/api/v1/notifications?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load notifications');
    return res.json();
  },

  async getUnreadCount(): Promise<number> {
    const res = await fetch('/api/v1/notifications/unread-count', {
      headers: getAuthHeaders(),
    });
    if (!res.ok) return 0;
    const data = await res.json();
    return data.unreadCount || 0;
  },

  async markRead(id: string): Promise<void> {
    const res = await fetch(`/api/v1/notifications/${id}/mark-read`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to mark notification as read');
  },

  async markAllRead(): Promise<void> {
    const res = await fetch('/api/v1/notifications/mark-all-read', {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to mark all as read');
  }
};
