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
    <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-md bg-slate-900 border-l border-slate-800 h-full flex flex-col shadow-2xl animate-in slide-in-from-right duration-300">
        {/* Drawer Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-teal-400" />
            <h3 className="font-semibold text-white text-base">Notifications</h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleMarkAllRead}
              className="text-xs text-teal-400 hover:text-teal-300 font-medium px-2 py-1 rounded hover:bg-slate-800 transition-colors"
            >
              Mark all read
            </button>
            <button
              onClick={onClose}
              className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* List of Notifications */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 rounded-xl bg-slate-800/40 animate-pulse"></div>
              ))}
            </div>
          ) : notifications.length === 0 ? (
            <div className="py-12 text-center text-slate-500">
              <Bell className="h-8 w-8 mx-auto mb-2 opacity-30" />
              <p className="text-sm">No notifications</p>
            </div>
          ) : (
            notifications.map((n) => {
              const isUnread = !n.readAt;
              return (
                <div
                  key={n.id}
                  className={`p-3.5 rounded-xl border transition-all ${
                    isUnread
                      ? 'bg-slate-800/80 border-teal-500/30 shadow-sm'
                      : 'bg-slate-900/40 border-slate-800 text-slate-400'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-xs font-semibold uppercase tracking-wider text-teal-400">
                      {n.type}
                    </span>
                    <span className="flex items-center gap-1 text-[11px] text-slate-500">
                      <Clock className="h-3 w-3" />
                      {new Date(n.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                  <h4 className={`text-sm font-semibold mt-1 ${isUnread ? 'text-white' : 'text-slate-300'}`}>
                    {n.title}
                  </h4>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-3">
                    {n.body}
                  </p>
                  <div className="mt-3 flex items-center justify-between pt-2 border-t border-slate-800/60">
                    {n.linkUrl ? (
                      <a
                        href={n.linkUrl}
                        className="inline-flex items-center gap-1 text-xs text-teal-400 hover:underline"
                      >
                        <span>View</span>
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    ) : <span />}
                    {isUnread && (
                      <button
                        onClick={() => handleMarkRead(n.id)}
                        className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-white transition-colors"
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
