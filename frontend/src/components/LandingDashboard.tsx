import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { documentApi } from '../api/documentApi';
import { notificationApi } from '../api/notificationApi';
import { DocumentSummaryDto } from '../api/documentTypes';
import { LibraryView } from './LibraryView';
import { DocumentDetailView } from './DocumentDetailView';
import { ApprovalsView } from './ApprovalsView';
import { TasksView } from './TasksView';
import { OperationsOverview } from './OperationsOverview';
import { AdminJobsView } from './AdminJobsView';
import { CopilotView } from './CopilotView';
import { NotificationDrawer } from './NotificationDrawer';
import { UploadModal } from './UploadModal';
import {
  ShieldCheck,
  LogOut,
  BookOpen,
  LayoutDashboard,
  CheckSquare,
  ListTodo,
  Bell,
  Cpu,
  Sparkles
} from 'lucide-react';

export const LandingDashboard: React.FC = () => {
  const { user, logout } = useAuth();
  const [documents, setDocuments] = useState<DocumentSummaryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'library' | 'copilot' | 'approvals' | 'tasks' | 'dashboard' | 'jobs'>('library');

  // Notifications state
  const [unreadCount, setUnreadCount] = useState(0);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);

  const canUpload = !!user?.roles.some((r) => r === 'ADMIN' || r === 'KNOWLEDGE_MANAGER');

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const page = await documentApi.listDocuments(0, 50);
      setDocuments(page.content);
    } catch {
      setDocuments([]);
    } finally {
      setLoading(false);
    }
  };

  const loadUnreadCount = async () => {
    try {
      const count = await notificationApi.getUnreadCount();
      setUnreadCount(count);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    loadDocuments();
    loadUnreadCount();
    const interval = setInterval(loadUnreadCount, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Navigation */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur-md px-6 py-4 flex items-center justify-between sticky top-0 z-40">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/30 flex items-center justify-center font-bold shadow-sm shadow-teal-500/10">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight text-white leading-tight">KnowFlow</h1>
              <p className="text-xs text-slate-400">Enterprise AI Knowledge & Workflow</p>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-1 bg-slate-950/50 p-1 rounded-xl border border-slate-800">
            <button
              onClick={() => { setActiveTab('library'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'library' && !selectedDocId ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
              }`}
            >
              <BookOpen className="h-3.5 w-3.5" />
              Document Library
            </button>
            <button
              onClick={() => { setActiveTab('copilot'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'copilot' && !selectedDocId ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
              }`}
            >
              <Sparkles className="h-3.5 w-3.5" />
              AI Copilot
            </button>
            <button
              onClick={() => { setActiveTab('approvals'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'approvals' ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
              }`}
            >
              <CheckSquare className="h-3.5 w-3.5" />
              Approvals
            </button>
            <button
              onClick={() => { setActiveTab('tasks'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'tasks' ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
              }`}
            >
              <ListTodo className="h-3.5 w-3.5" />
              Tasks
            </button>
            <button
              onClick={() => { setActiveTab('dashboard'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'dashboard' ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
              }`}
            >
              <LayoutDashboard className="h-3.5 w-3.5" />
              Operations Overview
            </button>
            {canUpload && (
              <button
                onClick={() => { setActiveTab('jobs'); setSelectedDocId(null); }}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'jobs' ? 'bg-slate-800 text-teal-400 shadow' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Cpu className="h-3.5 w-3.5" />
                Pipelines & Jobs
              </button>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {/* Notification Bell */}
          <button
            onClick={() => setIsNotificationsOpen(true)}
            className="relative p-2 rounded-lg border border-slate-800 bg-slate-900 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors"
            title="Notifications"
          >
            <Bell className="h-4 w-4" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 min-w-4 px-1 items-center justify-center rounded-full bg-teal-500 text-[10px] font-bold text-slate-950">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          <div className="text-right hidden sm:block">
            <div className="text-sm font-medium text-slate-200">{user?.displayName}</div>
            <div className="text-xs text-slate-400">{user?.email} · <span className="text-teal-400 font-semibold">{user?.roles.join(', ')}</span></div>
          </div>

          <button
            onClick={logout}
            className="rounded-lg border border-slate-700 bg-slate-800 p-2 text-slate-400 hover:text-white hover:bg-slate-700 transition-colors"
            title="Log Out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8">
        {selectedDocId ? (
          <DocumentDetailView
            documentId={selectedDocId}
            onBack={() => setSelectedDocId(null)}
            canEdit={canUpload}
          />
        ) : activeTab === 'library' ? (
          <LibraryView
            documents={documents}
            loading={loading}
            onSelectDoc={(id) => setSelectedDocId(id)}
            onOpenUpload={() => setIsUploadOpen(true)}
            canUpload={canUpload}
            onRefreshDocs={loadDocuments}
          />
        ) : activeTab === 'copilot' ? (
          <CopilotView
            onNavigateToDocument={(id) => {
              setSelectedDocId(id);
              setActiveTab('library');
            }}
          />
        ) : activeTab === 'approvals' ? (
          <ApprovalsView />
        ) : activeTab === 'tasks' ? (
          <TasksView />
        ) : activeTab === 'jobs' ? (
          <AdminJobsView />
        ) : (
          <OperationsOverview />
        )}
      </main>

      {/* Upload Modal */}
      <UploadModal
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onSuccess={() => loadDocuments()}
      />

      {/* Notification Drawer */}
      <NotificationDrawer
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
        onNotificationRead={loadUnreadCount}
      />
    </div>
  );
};
