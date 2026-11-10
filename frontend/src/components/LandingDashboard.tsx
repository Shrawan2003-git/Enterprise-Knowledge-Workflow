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
    <div className="min-h-screen clay-surface text-slate-100 flex flex-col">
      {/* Top Clay Navigation Bar */}
      <header className="clay-card mx-4 mt-4 mb-2 px-5 py-3.5 flex items-center justify-between sticky top-3 z-40 backdrop-blur-xl border border-slate-700/30">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-2xl clay-btn-teal text-white flex items-center justify-center font-bold shadow-md">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-base font-extrabold tracking-tight text-white leading-tight">Enterprise KnowFlow</h1>
              <p className="text-[11px] text-slate-400 font-medium">AI Knowledge & Workflow Copilot</p>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-1.5 bg-slate-950/70 p-1.5 rounded-2xl border border-slate-800/80">
            <button
              onClick={() => { setActiveTab('library'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'library' && !selectedDocId ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              <BookOpen className="h-4 w-4" />
              Document Library
            </button>
            <button
              onClick={() => { setActiveTab('copilot'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'copilot' && !selectedDocId ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              <Sparkles className="h-4 w-4" />
              AI Copilot
            </button>
            <button
              onClick={() => { setActiveTab('approvals'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'approvals' ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              <CheckSquare className="h-4 w-4" />
              Approvals
            </button>
            <button
              onClick={() => { setActiveTab('tasks'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'tasks' ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              <ListTodo className="h-4 w-4" />
              Tasks
            </button>
            <button
              onClick={() => { setActiveTab('dashboard'); setSelectedDocId(null); }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                activeTab === 'dashboard' ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              <LayoutDashboard className="h-4 w-4" />
              Operations Overview
            </button>
            {canUpload && (
              <button
                onClick={() => { setActiveTab('jobs'); setSelectedDocId(null); }}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all ${
                  activeTab === 'jobs' ? 'clay-pill-active text-white shadow-md' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Cpu className="h-4 w-4" />
                Pipelines & Jobs
              </button>
            )}
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {/* Notification Bell (Clay) */}
          <button
            onClick={() => setIsNotificationsOpen(true)}
            className="clay-btn-secondary relative p-2.5 rounded-xl text-slate-300 hover:text-white transition-transform hover:scale-105"
            title="Notifications"
          >
            <Bell className="h-4 w-4" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 min-w-4 px-1 items-center justify-center rounded-full bg-teal-400 text-[10px] font-extrabold text-slate-950 shadow">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          <div className="text-right hidden sm:block">
            <div className="text-xs font-bold text-white">{user?.displayName}</div>
            <div className="text-[11px] text-slate-400 mt-0.5 flex items-center justify-end gap-1.5">
              <span>{user?.email}</span>
              <span className="clay-badge-cyan px-2 py-0.5 text-[10px] font-bold uppercase">{user?.roles.join(', ')}</span>
            </div>
          </div>

          <button
            onClick={logout}
            className="clay-btn-secondary p-2.5 rounded-xl text-slate-400 hover:text-rose-400 transition-transform hover:scale-105"
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
