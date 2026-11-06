import React, { useState } from 'react';
import { approvalApi } from '../api/approvalApi';
import { X, Send, AlertCircle, CheckCircle2 } from 'lucide-react';

interface SubmitReviewModalProps {
  documentId: string;
  documentTitle: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const SubmitReviewModal: React.FC<SubmitReviewModalProps> = ({
  documentId,
  documentTitle,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [approverId, setApproverId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!approverId.trim()) {
      setError('Please provide the Approver UUID');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await approvalApi.submitReview(documentId, approverId.trim());
      setSuccess(true);
      setTimeout(() => {
        onSuccess();
        onClose();
        setSuccess(false);
      }, 1200);
    } catch (err: any) {
      setError(err.message || 'Failed to submit document for review');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-6 shadow-2xl relative">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div>
            <h3 className="text-lg font-bold text-white">Submit Document for Review</h3>
            <p className="text-xs text-slate-400 mt-0.5 line-clamp-1">{documentTitle}</p>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-400">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-teal-500/20 bg-teal-500/10 p-3 text-xs text-teal-400">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span>Submitted successfully! State transitioned to IN_REVIEW.</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-300">
              Assigned Approver User ID (UUID)
            </label>
            <input
              type="text"
              required
              placeholder="e.g. 00000000-0000-0000-0000-000000000001"
              value={approverId}
              onChange={(e) => setApproverId(e.target.value)}
              className="mt-1.5 block w-full rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none font-mono"
            />
            <p className="text-[11px] text-slate-500 mt-1">
              Note: Central governance prohibits self-approval (the submitter cannot approve their own document).
            </p>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-xl border border-slate-700 px-4 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || success}
              className="inline-flex items-center gap-2 rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white shadow-lg shadow-teal-500/20 hover:bg-teal-500 disabled:opacity-50 transition-all"
            >
              <Send className="h-3.5 w-3.5" />
              {loading ? 'Submitting...' : 'Submit for Approval'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
