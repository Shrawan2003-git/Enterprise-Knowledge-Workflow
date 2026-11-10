import React, { useEffect, useState } from 'react';
import { notificationApi, NotificationDto } from '../api/notificationApi';
import { Bell, Check, Clock, X, ExternalLink } from 'lucide-react';

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onNotificationRead?: () => void;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({
  isOpen,
  onClose,
  onNotificationRead,
}) => {
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const page = await notificationApi.getMyNotifications(0, 30);
      setNotifications(page.content);
    } catch {
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
    }
  }, [isOpen]);

  const handleMarkRead = async (id: string) => {
    try {
      await notificationApi.markRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n))
      );
      onNotificationRead?.();
    } catch {
      // ignore
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await notificationApi.markAllRead();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, readAt: new Date().toISOString() }))
      );
      onNotificationRead?.();
    } catch {
      // ignore
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/75 backdrop-blur-md animate-in fade-in duration-200">
      <div className="w-full max-w-md clay-card-elevated h-full flex flex-col shadow-2xl animate-in slide-in-from-right duration-300 rounded-l-3xl border-r-0">
        {/* Drawer Header */}
        <div className="p-5 border-b border-slate-700/40 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="h-9 w-9 rounded-xl clay-btn-teal flex items-center justify-center text-white font-bold">
              <Bell className="h-4 w-4" />
            </div>
            <h3 className="font-black text-white text-base">Notifications</h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleMarkAllRead}
              className="clay-btn-secondary text-xs text-teal-400 hover:text-white font-bold px-3 py-1.5 transition-all"
            >
              Mark all read
            </button>
            <button
              onClick={onClose}
              className="clay-btn-secondary p-2 text-slate-400 hover:text-white transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* List of Notifications */}
        <div className="flex-1 overflow-y-auto p-5 space-y-3.5">
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 rounded-2xl clay-card animate-pulse"></div>
              ))}
            </div>
          ) : notifications.length === 0 ? (
            <div className="py-16 text-center text-slate-500 font-medium">
              <Bell className="h-10 w-10 mx-auto mb-2 opacity-30" />
              <p className="text-sm">No notifications</p>
            </div>
          ) : (
            notifications.map((n) => {
              const isUnread = !n.readAt;
              return (
                <div
                  key={n.id}
                  className={`p-4 rounded-2xl transition-all ${
                    isUnread
                      ? 'clay-card-elevated border border-teal-500/30'
                      : 'clay-card text-slate-400'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="clay-badge-cyan px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider">
                      {n.type}
                    </span>
                    <span className="flex items-center gap-1 text-[11px] text-slate-400 font-medium">
                      <Clock className="h-3 w-3" />
                      {new Date(n.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                  <h4 className={`text-sm font-bold mt-2 ${isUnread ? 'text-white' : 'text-slate-300'}`}>
                    {n.title}
                  </h4>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-3 leading-relaxed">
                    {n.body}
                  </p>
                  <div className="mt-3.5 flex items-center justify-between pt-2.5 border-t border-slate-700/40">
                    {n.linkUrl ? (
                      <a
                        href={n.linkUrl}
                        className="inline-flex items-center gap-1 text-xs text-teal-400 hover:text-teal-300 font-bold"
                      >
                        <span>View</span>
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    ) : <span />}
                    {isUnread && (
                      <button
                        onClick={() => handleMarkRead(n.id)}
                        className="clay-btn-secondary inline-flex items-center gap-1 text-xs px-2.5 py-1 text-slate-300 hover:text-white transition-all font-bold"
                      >
                        <Check className="h-3.5 w-3.5" />
                        <span>Mark read</span>
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
