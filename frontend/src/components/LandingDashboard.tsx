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
    <div className="min-h-screen bg-scenic-horizon text-slate-100 flex flex-col p-3 sm:p-5 lg:p-8 selection:bg-blue-600 selection:text-white">
      {/* Main Floating Frosted Acrylic Console Window (Matches Reference Image) */}
      <div className="max-w-7xl w-full mx-auto acrylic-console p-5 sm:p-7 lg:p-8 flex flex-col md:flex-row gap-6 relative shadow-2xl min-h-[820px]">
        
        {/* Left Sidebar Navigation */}
        <aside className="w-full md:w-56 flex-shrink-0 flex flex-col justify-between border-b md:border-b-0 md:border-r border-slate-700/40 pb-6 md:pb-0 md:pr-6">
          <div className="space-y-7">
            {/* Brand Logo */}
            <div className="flex items-center gap-2.5 px-2">
              <div className="h-9 w-9 rounded-xl bg-blue-600 flex items-center justify-center text-white font-extrabold shadow-lg shadow-blue-500/50">
                <ShieldCheck className="h-5 w-5" />
              </div>
              <div>
                <span className="text-sm font-extrabold tracking-tight text-white block leading-tight">Enterprise</span>
                <span className="text-xs font-semibold text-blue-400">KnowFlow</span>
              </div>
            </div>

            {/* Nav Items List */}
            <nav className="space-y-2">
              <button
                onClick={() => { setActiveTab('library'); setSelectedDocId(null); }}
                className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                  activeTab === 'library' && !selectedDocId
                    ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                }`}
              >
                {activeTab === 'library' && !selectedDocId ? (
                  <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                    <BookOpen className="h-3 w-3" />
                  </div>
                ) : (
                  <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                    <BookOpen className="h-3 w-3" />
                  </div>
                )}
                <span>Library</span>
              </button>

              <button
                onClick={() => { setActiveTab('copilot'); setSelectedDocId(null); }}
                className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                  activeTab === 'copilot' && !selectedDocId
                    ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                }`}
              >
                {activeTab === 'copilot' && !selectedDocId ? (
                  <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                    <Sparkles className="h-3 w-3" />
                  </div>
                ) : (
                  <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                    <Sparkles className="h-3 w-3" />
                  </div>
                )}
                <span>AI Copilot</span>
              </button>

              <button
                onClick={() => { setActiveTab('approvals'); setSelectedDocId(null); }}
                className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                  activeTab === 'approvals'
                    ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                }`}
              >
                {activeTab === 'approvals' ? (
                  <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                    <CheckSquare className="h-3 w-3" />
                  </div>
                ) : (
                  <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                    <CheckSquare className="h-3 w-3" />
                  </div>
                )}
                <span>Approvals</span>
              </button>

              <button
                onClick={() => { setActiveTab('tasks'); setSelectedDocId(null); }}
                className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                  activeTab === 'tasks'
                    ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                }`}
              >
                {activeTab === 'tasks' ? (
                  <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                    <ListTodo className="h-3 w-3" />
                  </div>
                ) : (
                  <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                    <ListTodo className="h-3 w-3" />
                  </div>
                )}
                <span>Tasks</span>
              </button>

              <button
                onClick={() => { setActiveTab('dashboard'); setSelectedDocId(null); }}
                className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                  activeTab === 'dashboard'
                    ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                }`}
              >
                {activeTab === 'dashboard' ? (
                  <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                    <LayoutDashboard className="h-3 w-3" />
                  </div>
                ) : (
                  <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                    <LayoutDashboard className="h-3 w-3" />
                  </div>
                )}
                <span>Operations</span>
              </button>

              {canUpload && (
                <button
                  onClick={() => { setActiveTab('jobs'); setSelectedDocId(null); }}
                  className={`w-full flex items-center gap-3.5 px-3 py-2.5 rounded-2xl text-xs font-bold transition-all ${
                    activeTab === 'jobs'
                      ? 'bg-blue-950/40 border border-blue-500/30 text-white shadow-lg shadow-blue-900/30'
                      : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
                  }`}
                >
                  {activeTab === 'jobs' ? (
                    <div className="h-6 w-6 glossy-sphere-icon flex items-center justify-center text-white">
                      <Cpu className="h-3 w-3" />
                    </div>
                  ) : (
                    <div className="h-6 w-6 rounded-full bg-slate-800/60 flex items-center justify-center text-slate-400">
                      <Cpu className="h-3 w-3" />
                    </div>
                  )}
                  <span>Pipelines</span>
                </button>
              )}
            </nav>
          </div>

          {/* Bottom Settings & Log out */}
          <div className="pt-6 border-t border-slate-700/40 space-y-2">
            <button
              onClick={logout}
              className="w-full flex items-center gap-3.5 px-3 py-2 rounded-2xl text-slate-400 hover:text-rose-400 text-xs font-medium transition-all"
            >
              <LogOut className="h-4 w-4" />
              <span>Log out</span>
            </button>
          </div>
        </aside>

        {/* Right / Center Workspace */}
        <div className="flex-1 flex flex-col gap-5 overflow-hidden">
          
          {/* Top Bar: Breadcrumb + Search + Notifications + Profile */}
          <div className="flex flex-wrap items-center justify-between gap-4 pb-2 border-b border-slate-700/40">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span>Home</span>
              <span>/</span>
              <span className="text-slate-200 font-semibold uppercase">{activeTab}</span>
              <span>/</span>
              <span className="text-blue-300 font-medium">Enterprise Workspace</span>
            </div>

            {/* Profile & Notifications Stack */}
            <div className="flex items-center gap-3">
              <button
                onClick={() => setIsNotificationsOpen(true)}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/50 relative transition-colors"
                title="Notifications"
              >
                <Bell className="h-4 w-4" />
                {unreadCount > 0 && (
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-blue-400 shadow-[0_0_8px_#60a5fa]"></span>
                )}
              </button>

              <div className="h-4 w-px bg-slate-700/60 mx-1 hidden sm:block"></div>

              <div className="text-right hidden sm:block">
                <div className="text-xs font-bold text-white leading-tight">{user?.displayName}</div>
                <div className="text-[10px] text-blue-300 font-medium">{user?.roles.join(', ')}</div>
              </div>

              <div className="h-8 w-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-extrabold shadow-md shadow-blue-500/40">
                {user?.displayName ? user.displayName.charAt(0).toUpperCase() : 'U'}
              </div>
            </div>
          </div>

          {/* Main View Content Area */}
          <div className="flex-1 overflow-y-auto pr-1">
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
          </div>
        </div>

      </div>

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
