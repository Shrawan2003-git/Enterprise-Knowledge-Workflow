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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg clay-card-elevated rounded-3xl p-6 relative">
        <div className="flex items-center justify-between pb-4 border-b border-slate-700/40">
          <div>
            <h3 className="text-lg font-black text-white">Submit Document for Review</h3>
            <p className="text-xs text-slate-400 mt-0.5 line-clamp-1">{documentTitle}</p>
          </div>
          <button
            onClick={onClose}
            className="clay-btn-secondary rounded-xl p-2 text-slate-400 hover:text-white transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {error && (
          <div className="mt-4 flex items-center gap-2 rounded-2xl clay-badge-rose p-3.5 text-xs font-bold">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="mt-4 flex items-center gap-2 rounded-2xl clay-badge-emerald p-3.5 text-xs font-bold">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span>Submitted successfully! State transitioned to IN_REVIEW.</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-300">
              Assigned Approver User ID (UUID)
            </label>
            <input
              type="text"
              required
              placeholder="e.g. 00000000-0000-0000-0000-000000000001"
              value={approverId}
              onChange={(e) => setApproverId(e.target.value)}
              className="mt-1.5 block w-full rounded-2xl clay-input px-4 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none font-mono"
            />
            <p className="text-[11px] text-slate-400 mt-1 font-medium">
              Note: Central governance prohibits self-approval (the submitter cannot approve their own document).
            </p>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-700/40">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="clay-btn-secondary px-4 py-2 text-xs font-bold text-slate-300 hover:text-white transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || success}
              className="clay-btn-teal inline-flex items-center gap-2 px-5 py-2 text-xs font-bold text-white disabled:opacity-50 transition-all"
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
