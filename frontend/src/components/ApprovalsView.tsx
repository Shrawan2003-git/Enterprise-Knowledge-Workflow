import React, { useEffect, useState } from 'react';
import { approvalApi, ApprovalRequestDto } from '../api/approvalApi';
import { CheckCircle2, XCircle, AlertCircle, History, FileCheck2, UserCheck } from 'lucide-react';

export const ApprovalsView: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'assigned' | 'submitted'>('assigned');
  const [approvals, setApprovals] = useState<ApprovalRequestDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionModal, setActionModal] = useState<{
    type: 'approve' | 'reject' | 'changes';
    request: ApprovalRequestDto;
  } | null>(null);
  const [comment, setComment] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [expandedHistoryId, setExpandedHistoryId] = useState<string | null>(null);

  const fetchApprovals = async () => {
    setLoading(true);
    try {
      if (activeTab === 'assigned') {
        const page = await approvalApi.listAssigned(0, 50);
        setApprovals(page.content);
      } else {
        const page = await approvalApi.listSubmitted(0, 50);
        setApprovals(page.content);
      }
    } catch {
      setApprovals([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApprovals();
  }, [activeTab]);

  const handleAction = async () => {
    if (!actionModal) return;
    const { type, request } = actionModal;
    if ((type === 'reject' || type === 'changes') && !comment.trim()) {
      setActionError(`A comment explaining the decision is required for ${type === 'reject' ? 'rejection' : 'requesting revisions'}.`);
      return;
    }

    setSubmitting(true);
    setActionError(null);
    try {
      if (type === 'approve') {
        await approvalApi.approve(request.id, comment.trim() || undefined);
      } else if (type === 'reject') {
        await approvalApi.reject(request.id, comment.trim());
      } else {
        await approvalApi.requestChanges(request.id, comment.trim());
      }
      setActionModal(null);
      setComment('');
      fetchApprovals();
    } catch (err: any) {
      setActionError(err.message || 'Workflow action failed');
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'REJECTED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      case 'CHANGES_REQUESTED':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'PENDING':
      default:
        return 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header and Filter Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white tracking-tight">Workflow Approvals</h2>
          <p className="text-sm text-slate-400">
            Governed state-machine reviews enforcing 4-eye separation of duties and append-only audit history.
          </p>
        </div>

        <div className="flex items-center gap-1 bg-slate-900 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('assigned')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'assigned'
                ? 'bg-slate-800 text-teal-400 shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Assigned to Me
          </button>
          <button
            onClick={() => setActiveTab('submitted')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'submitted'
                ? 'bg-slate-800 text-teal-400 shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Submitted by Me
          </button>
        </div>
      </div>

      {/* Content list */}
      {loading ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-32 rounded-xl bg-slate-900/50 border border-slate-800 animate-pulse"></div>
          ))}
        </div>
      ) : approvals.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/30 p-12 text-center">
          <FileCheck2 className="mx-auto h-12 w-12 text-slate-600" />
          <h3 className="mt-4 text-base font-semibold text-white">No approval requests</h3>
          <p className="mt-1 text-sm text-slate-400">
            {activeTab === 'assigned'
              ? 'You have no pending documents awaiting your review.'
              : 'You have not submitted any documents for review yet.'}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {approvals.map((req) => (
            <div
              key={req.id}
              className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 backdrop-blur-sm space-y-4"
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-lg bg-slate-800 flex items-center justify-center text-teal-400">
                    <UserCheck className="h-5 w-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-slate-400">
                        Req ID: {req.id.substring(0, 8)}...
                      </span>
                      <span
                        className={`inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold ${getStatusBadge(
                          req.status
                        )}`}
                      >
                        {req.status}
                      </span>
                    </div>
                    <div className="text-xs text-slate-400 mt-1 flex items-center gap-3">
                      <span>Submitted: {new Date(req.submittedAt).toLocaleString()}</span>
                      {req.completedAt && (
                        <span>Completed: {new Date(req.completedAt).toLocaleString()}</span>
                      )}
                    </div>
                  </div>
                </div>

                {activeTab === 'assigned' && req.status === 'PENDING' && (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => {
                        setActionModal({ type: 'approve', request: req });
                        setComment('');
                        setActionError(null);
                      }}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600/90 hover:bg-teal-600 px-3 py-1.5 text-xs font-semibold text-white shadow transition-colors"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      Approve
                    </button>
                    <button
                      onClick={() => {
                        setActionModal({ type: 'changes', request: req });
                        setComment('');
                        setActionError(null);
                      }}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-amber-600/90 hover:bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white shadow transition-colors"
                    >
                      <AlertCircle className="h-3.5 w-3.5" />
                      Request Changes
                    </button>
                    <button
                      onClick={() => {
                        setActionModal({ type: 'reject', request: req });
                        setComment('');
                        setActionError(null);
                      }}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600/90 hover:bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white shadow transition-colors"
                    >
                      <XCircle className="h-3.5 w-3.5" />
                      Reject
                    </button>
                  </div>
                )}
              </div>

              {/* History Toggle */}
              {req.history && req.history.length > 0 && (
                <div className="pt-2 border-t border-slate-800/80">
                  <button
                    onClick={() =>
                      setExpandedHistoryId(expandedHistoryId === req.id ? null : req.id)
                    }
                    className="inline-flex items-center gap-1 text-xs text-teal-400 hover:text-teal-300 font-medium"
                  >
                    <History className="h-3.5 w-3.5" />
                    <span>
                      {expandedHistoryId === req.id ? 'Hide Audit History' : `View Audit History (${req.history.length})`}
                    </span>
                  </button>

                  {expandedHistoryId === req.id && (
                    <div className="mt-3 space-y-2 pl-3 border-l-2 border-slate-800">
                      {req.history.map((h) => (
                        <div key={h.id} className="text-xs space-y-0.5">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-300">{h.action}</span>
                            <span className="text-slate-500 font-mono text-[11px]">
                              ({h.fromState} → {h.toState})
                            </span>
                            <span className="text-slate-500 text-[11px]">
                              {new Date(h.createdAt).toLocaleDateString()}
                            </span>
                          </div>
                          {h.comment && (
                            <p className="text-slate-400 italic bg-slate-950/40 p-1.5 rounded-md border border-slate-800">
                              "{h.comment}"
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Action Dialog Modal */}
      {actionModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
          <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl relative">
            <h3 className="text-lg font-bold text-white capitalize">
              {actionModal.type === 'approve'
                ? 'Approve Document'
                : actionModal.type === 'reject'
                ? 'Reject Document'
                : 'Request Revisions'}
            </h3>
            <p className="text-xs text-slate-400 mt-1">
              {actionModal.type === 'approve'
                ? 'Approving marks this version as officially APPROVED and sets it as current.'
                : actionModal.type === 'reject'
                ? 'Rejection is a terminal state transition. Please provide a clear rationale.'
                : 'Requesting changes transitions state to CHANGES_REQUESTED for the author.'}
            </p>

            {actionError && (
              <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-400">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <div className="mt-4">
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
                Decision Note / Comment {actionModal.type !== 'approve' && <span className="text-rose-400">*</span>}
              </label>
              <textarea
                rows={3}
                required={actionModal.type !== 'approve'}
                placeholder={
                  actionModal.type === 'approve'
                    ? 'Optional approval comments...'
                    : 'Explain reasons or revision requirements...'
                }
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none"
              />
            </div>

            <div className="mt-6 flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
              <button
                type="button"
                onClick={() => setActionModal(null)}
                disabled={submitting}
                className="rounded-xl border border-slate-700 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleAction}
                disabled={submitting}
                className={`inline-flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-lg transition-all ${
                  actionModal.type === 'approve'
                    ? 'bg-teal-600 hover:bg-teal-500 shadow-teal-500/20'
                    : actionModal.type === 'reject'
                    ? 'bg-rose-600 hover:bg-rose-500 shadow-rose-500/20'
                    : 'bg-amber-600 hover:bg-amber-500 shadow-amber-500/20'
                }`}
              >
                {submitting ? 'Recording...' : 'Confirm Decision'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
