import React, { useEffect, useState } from 'react';
import { taskApi, TaskDto, CreateTaskRequest } from '../api/taskApi';
import { CheckCircle2, Plus, AlertTriangle, ArrowRight, MessageSquare, X } from 'lucide-react';

export const TasksView: React.FC = () => {
  const [tasks, setTasks] = useState<TaskDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedTaskForComment, setSelectedTaskForComment] = useState<TaskDto | null>(null);
  const [newComment, setNewComment] = useState('');
  const [submittingComment, setSubmittingComment] = useState(false);

  // Form state
  const [formTitle, setFormTitle] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formPriority, setFormPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'>('MEDIUM');
  const [formDueAt, setFormDueAt] = useState('');
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const fetchTasks = async () => {
    setLoading(true);
    try {
      const page = await taskApi.listMyTasks(0, 100);
      setTasks(page.content);
    } catch {
      setTasks([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTasks();
  }, []);

  const handleCreateTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim()) {
      setFormError('Title is required');
      return;
    }
    setCreating(true);
    setFormError(null);
    try {
      const req: CreateTaskRequest = {
        title: formTitle.trim(),
        description: formDescription.trim() || undefined,
        priority: formPriority,
        dueAt: formDueAt ? new Date(formDueAt).toISOString() : undefined,
      };
      await taskApi.createTask(req);
      setIsCreateOpen(false);
      setFormTitle('');
      setFormDescription('');
      setFormDueAt('');
      setFormPriority('MEDIUM');
      fetchTasks();
    } catch (err: any) {
      setFormError(err.message || 'Failed to create task');
    } finally {
      setCreating(false);
    }
  };

  const handleUpdateStatus = async (task: TaskDto, newStatus: string) => {
    try {
      await taskApi.updateStatus(task.id, newStatus, task.version);
      fetchTasks();
    } catch (err: any) {
      alert(err.message || 'Status transition failed');
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTaskForComment || !newComment.trim()) return;
    setSubmittingComment(true);
    try {
      await taskApi.addComment(selectedTaskForComment.id, newComment.trim());
      setNewComment('');
      fetchTasks();
      // Update local comments
      setSelectedTaskForComment((prev) =>
        prev
          ? {
              ...prev,
              comments: [
                ...prev.comments,
                {
                  id: 'temp-' + Date.now(),
                  authorId: 'me',
                  content: newComment.trim(),
                  createdAt: new Date().toISOString(),
                },
              ],
            }
          : null
      );
    } catch (err: any) {
      alert(err.message || 'Failed to post comment');
    } finally {
      setSubmittingComment(false);
    }
  };

  const getPriorityBadge = (priority: string) => {
    switch (priority) {
      case 'URGENT':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      case 'HIGH':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'MEDIUM':
        return 'bg-sky-500/10 text-sky-400 border-sky-500/30';
      case 'LOW':
      default:
        return 'bg-slate-700/60 text-slate-400 border-slate-600';
    }
  };

  const columns: Array<{ id: 'OPEN' | 'IN_PROGRESS' | 'DONE'; title: string; color: string }> = [
    { id: 'OPEN', title: 'Open', color: 'border-slate-700' },
    { id: 'IN_PROGRESS', title: 'In Progress', color: 'border-sky-500/40' },
    { id: 'DONE', title: 'Done', color: 'border-emerald-500/40' },
  ];

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white tracking-tight">Tasks Workspace</h2>
          <p className="text-sm text-slate-400">
            Collaborative, permission-governed action items linked directly to documents and approvals.
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-teal-500/20 hover:bg-teal-500 transition-all"
        >
          <Plus className="h-4 w-4" />
          Create Task
        </button>
      </div>

      {/* Kanban Board */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-80 rounded-xl bg-slate-900/50 border border-slate-800 animate-pulse"></div>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {columns.map((col) => {
            const colTasks = tasks.filter((t) => t.status === col.id);
            return (
              <div
                key={col.id}
                className="rounded-2xl border border-slate-800 bg-slate-900/40 p-4 flex flex-col min-h-[500px]"
              >
                <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-4">
                  <h3 className="font-semibold text-slate-200 text-sm flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-teal-400" />
                    {col.title}
                  </h3>
                  <span className="text-xs font-mono px-2 py-0.5 rounded-full bg-slate-800 text-slate-400">
                    {colTasks.length}
                  </span>
                </div>

                <div className="flex-1 space-y-3 overflow-y-auto">
                  {colTasks.length === 0 ? (
                    <div className="text-center py-12 text-slate-600 text-xs italic">
                      No tasks in {col.title}
                    </div>
                  ) : (
                    colTasks.map((t) => {
                      const isOverdue =
                        t.dueAt &&
                        new Date(t.dueAt).getTime() < Date.now() &&
                        t.status !== 'DONE';
                      return (
                        <div
                          key={t.id}
                          className="rounded-xl border border-slate-800 bg-slate-900/90 p-4 shadow-sm hover:border-slate-700 transition-all space-y-3"
                        >
                          <div className="flex items-start justify-between gap-2">
                            <span
                              className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[10px] font-semibold ${getPriorityBadge(
                                t.priority
                              )}`}
                            >
                              {t.priority}
                            </span>
                            {isOverdue && (
                              <span className="inline-flex items-center gap-1 text-[10px] text-rose-400 font-semibold">
                                <AlertTriangle className="h-3 w-3" /> Overdue
                              </span>
                            )}
                          </div>

                          <h4 className="text-sm font-semibold text-white leading-tight">
                            {t.title}
                          </h4>
                          {t.description && (
                            <p className="text-xs text-slate-400 line-clamp-2">
                              {t.description}
                            </p>
                          )}

                          <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-500">
                            <button
                              onClick={() => setSelectedTaskForComment(t)}
                              className="inline-flex items-center gap-1 text-slate-400 hover:text-teal-400 transition-colors"
                            >
                              <MessageSquare className="h-3.5 w-3.5" />
                              <span>{t.comments?.length || 0}</span>
                            </button>

                            <div className="flex items-center gap-2">
                              {col.id === 'OPEN' && (
                                <button
                                  onClick={() => handleUpdateStatus(t, 'IN_PROGRESS')}
                                  title="Move to In Progress"
                                  className="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
                                >
                                  <ArrowRight className="h-3.5 w-3.5" />
                                </button>
                              )}
                              {col.id === 'IN_PROGRESS' && (
                                <button
                                  onClick={() => handleUpdateStatus(t, 'DONE')}
                                  title="Mark Done"
                                  className="p-1 rounded bg-teal-500/20 text-teal-400 hover:bg-teal-500/30 transition-colors"
                                >
                                  <CheckCircle2 className="h-3.5 w-3.5" />
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Task Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
          <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl relative">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="text-lg font-bold text-white">Create New Task</h3>
              <button
                onClick={() => setIsCreateOpen(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {formError && (
              <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-400">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <form onSubmit={handleCreateTask} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                  Task Title *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Complete Q3 Governance Audit"
                  value={formTitle}
                  onChange={(e) => setFormTitle(e.target.value)}
                  className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                  Description
                </label>
                <textarea
                  rows={2}
                  placeholder="Provide context or instructions..."
                  value={formDescription}
                  onChange={(e) => setFormDescription(e.target.value)}
                  className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                    Priority
                  </label>
                  <select
                    value={formPriority}
                    onChange={(e: any) => setFormPriority(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-teal-500 focus:outline-none"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="URGENT">Urgent</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                    Due Date
                  </label>
                  <input
                    type="date"
                    value={formDueAt}
                    onChange={(e) => setFormDueAt(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-teal-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  disabled={creating}
                  className="rounded-xl border border-slate-700 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  className="rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-teal-500/20 hover:bg-teal-500 transition-all"
                >
                  {creating ? 'Creating...' : 'Create Task'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Task Comments Modal */}
      {selectedTaskForComment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
          <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl relative flex flex-col max-h-[85vh]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="text-base font-bold text-white leading-tight">
                  {selectedTaskForComment.title}
                </h3>
                <span className="text-xs text-slate-400">Discussion Thread</span>
              </div>
              <button
                onClick={() => setSelectedTaskForComment(null)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-4 space-y-3">
              {(!selectedTaskForComment.comments || selectedTaskForComment.comments.length === 0) ? (
                <div className="text-center py-8 text-xs text-slate-500 italic">
                  No comments posted on this task yet.
                </div>
              ) : (
                selectedTaskForComment.comments.map((c) => (
                  <div key={c.id} className="rounded-xl bg-slate-950/60 border border-slate-800 p-3 space-y-1">
                    <div className="flex items-center justify-between text-[11px] text-slate-400">
                      <span className="font-semibold text-teal-400">Collaborator</span>
                      <span>{new Date(c.createdAt).toLocaleString()}</span>
                    </div>
                    <p className="text-xs text-slate-200">{c.content}</p>
                  </div>
                ))
              )}
            </div>

            <form onSubmit={handleAddComment} className="pt-3 border-t border-slate-800 flex gap-2">
              <input
                type="text"
                placeholder="Write a comment..."
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                className="flex-1 rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none"
              />
              <button
                type="submit"
                disabled={submittingComment || !newComment.trim()}
                className="rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white hover:bg-teal-500 disabled:opacity-50 transition-colors"
              >
                Post
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
