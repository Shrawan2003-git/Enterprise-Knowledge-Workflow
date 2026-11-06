import React, { useState, useEffect } from 'react';
import { 
  RefreshCw, AlertTriangle, CheckCircle2, Clock, 
  RotateCcw, ShieldAlert, Cpu, Layers
} from 'lucide-react';
import { ingestionApi, AdminJobDto } from '../api/ingestionApi';

export const AdminJobsView: React.FC = () => {
  const [jobs, setJobs] = useState<AdminJobDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedJob, setSelectedJob] = useState<AdminJobDto | null>(null);
  const [retryReason, setRetryReason] = useState('');
  const [retryLoading, setRetryLoading] = useState(false);
  const [reprocessDocId, setReprocessDocId] = useState('');
  const [reprocessPipelineVer, setReprocessPipelineVer] = useState('v2');
  const [reprocessReason, setReprocessReason] = useState('');
  const [showReprocessModal, setShowReprocessModal] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const loadJobs = async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const res = await ingestionApi.listJobs(statusFilter, undefined, 0, 50);
      setJobs(res.content || []);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to load pipeline jobs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadJobs();
  }, [statusFilter]);

  const handleRetry = async () => {
    if (!selectedJob) return;
    if (!retryReason.trim()) {
      setErrorMsg('Audit reason is required to retry an ingestion job.');
      return;
    }
    try {
      setRetryLoading(true);
      setErrorMsg(null);
      await ingestionApi.retryJob(selectedJob.id, retryReason);
      setSuccessMsg(`Job ${selectedJob.id.substring(0, 8)} successfully queued for retry.`);
      setSelectedJob(null);
      setRetryReason('');
      await loadJobs();
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to trigger retry');
    } finally {
      setRetryLoading(false);
    }
  };

  const handleReprocess = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reprocessDocId || !reprocessPipelineVer || !reprocessReason) {
      setErrorMsg('All fields are required for document reprocessing.');
      return;
    }
    try {
      setRetryLoading(true);
      setErrorMsg(null);
      await ingestionApi.reprocessDocument(reprocessDocId, reprocessPipelineVer, reprocessReason);
      setSuccessMsg(`Reprocessing queued under pipeline ${reprocessPipelineVer}. Active index will switch atomically upon completion.`);
      setShowReprocessModal(false);
      setReprocessDocId('');
      setReprocessReason('');
      await loadJobs();
    } catch (err: any) {
      setErrorMsg(err.message || 'Reprocessing request failed');
    } finally {
      setRetryLoading(false);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'RUNNING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300 animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
            RUNNING
          </span>
        );
      case 'SUCCEEDED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300">
            <CheckCircle2 className="w-3.5 h-3.5" />
            SUCCEEDED
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300">
            <ShieldAlert className="w-3.5 h-3.5" />
            FAILED
          </span>
        );
      case 'RETRY_SCHEDULED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
            <RotateCcw className="w-3.5 h-3.5" />
            RETRY PENDING
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300">
            <Clock className="w-3.5 h-3.5" />
            PENDING
          </span>
        );
    }
  };

  const counts = {
    running: jobs.filter(j => j.status === 'RUNNING').length,
    failed: jobs.filter(j => j.status === 'FAILED').length,
    succeeded: jobs.filter(j => j.status === 'SUCCEEDED').length,
    pending: jobs.filter(j => j.status === 'PENDING' || j.status === 'RETRY_SCHEDULED').length,
  };

  return (
    <div className="space-y-6">
      {/* Header and KPI summary */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white dark:bg-slate-900 p-6 rounded-2xl border border-slate-200/80 dark:border-slate-800 shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <Cpu className="w-6 h-6 text-indigo-600 dark:text-indigo-400" />
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">Ingestion Pipeline Engine</h2>
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Governed asynchronous background workers, PDF parsing, chunk indexing, and atomic reprocessing.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowReprocessModal(true)}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-indigo-600 bg-indigo-50 hover:bg-indigo-100 dark:bg-indigo-950/50 dark:text-indigo-300 rounded-xl transition"
          >
            <Layers className="w-4 h-4" />
            Reprocess Pipeline
          </button>
          <button
            onClick={loadJobs}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-200 rounded-xl transition"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-blue-50 dark:bg-blue-950/30 border border-blue-200/60 dark:border-blue-900/50">
          <div className="text-xs font-semibold text-blue-700 dark:text-blue-300 uppercase tracking-wider">Active Workers</div>
          <div className="text-2xl font-bold text-blue-900 dark:text-blue-100 mt-1">{counts.running}</div>
        </div>
        <div className="p-4 rounded-xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200/60 dark:border-amber-900/50">
          <div className="text-xs font-semibold text-amber-700 dark:text-amber-300 uppercase tracking-wider">Queued Jobs</div>
          <div className="text-2xl font-bold text-amber-900 dark:text-amber-100 mt-1">{counts.pending}</div>
        </div>
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200/60 dark:border-emerald-900/50">
          <div className="text-xs font-semibold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider">Indexed Ready</div>
          <div className="text-2xl font-bold text-emerald-900 dark:text-emerald-100 mt-1">{counts.succeeded}</div>
        </div>
        <div className="p-4 rounded-xl bg-rose-50 dark:bg-rose-950/30 border border-rose-200/60 dark:border-rose-900/50">
          <div className="text-xs font-semibold text-rose-700 dark:text-rose-300 uppercase tracking-wider">Dead Letter Failures</div>
          <div className="text-2xl font-bold text-rose-900 dark:text-rose-100 mt-1">{counts.failed}</div>
        </div>
      </div>

      {/* Feedback alerts */}
      {errorMsg && (
        <div className="p-4 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 text-sm flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}
      {successMsg && (
        <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300 text-sm flex items-center gap-3">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2 border-b border-slate-200 dark:border-slate-800">
        {['ALL', 'PENDING', 'RUNNING', 'FAILED', 'RETRY_SCHEDULED', 'SUCCEEDED'].map((st) => (
          <button
            key={st}
            onClick={() => setStatusFilter(st)}
            className={`px-4 py-2 rounded-xl text-xs font-semibold transition ${
              statusFilter === st
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            {st}
          </button>
        ))}
      </div>

      {/* Jobs Table */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200/80 dark:border-slate-800 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
            <thead className="bg-slate-50 dark:bg-slate-800/60 text-xs font-semibold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4">Job ID / Pipeline</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4">Attempts</th>
                <th className="px-6 py-4">Worker Lock</th>
                <th className="px-6 py-4">Error Diagnostics</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
              {jobs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-slate-400">
                    No ingestion jobs matching filter "{statusFilter}".
                  </td>
                </tr>
              ) : (
                jobs.map((job) => (
                  <tr key={job.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40 transition">
                    <td className="px-6 py-4">
                      <div className="font-mono text-xs font-medium text-slate-900 dark:text-white">
                        {job.id.substring(0, 8)}...
                      </div>
                      <div className="inline-flex items-center gap-1 text-xs text-indigo-600 dark:text-indigo-400 mt-0.5">
                        <Layers className="w-3 h-3" />
                        {job.pipelineVersion}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {getStatusBadge(job.status)}
                    </td>
                    <td className="px-6 py-4">
                      <span className="font-semibold text-slate-700 dark:text-slate-200">
                        {job.attempts}
                      </span>
                      <span className="text-slate-400 text-xs"> / {job.maxAttempts}</span>
                    </td>
                    <td className="px-6 py-4">
                      {job.lockedBy ? (
                        <span className="inline-flex items-center gap-1 font-mono text-xs text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded">
                          <Cpu className="w-3 h-3 text-slate-400" />
                          {job.lockedBy}
                        </span>
                      ) : (
                        <span className="text-slate-400 text-xs">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4 max-w-xs">
                      {job.errorCode ? (
                        <div>
                          <span className="inline-block text-xs font-mono font-semibold px-2 py-0.5 rounded bg-rose-50 text-rose-700 dark:bg-rose-950/50 dark:text-rose-400 border border-rose-200/50 dark:border-rose-900/50">
                            {job.errorCode}
                          </span>
                          {job.errorMessage && (
                            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 truncate" title={job.errorMessage}>
                              {job.errorMessage}
                            </p>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">None</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right">
                      {job.status === 'FAILED' && (
                        <button
                          onClick={() => {
                            setSelectedJob(job);
                            setRetryReason('');
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-indigo-50 hover:bg-indigo-100 dark:bg-indigo-950/50 dark:hover:bg-indigo-900/50 text-indigo-600 dark:text-indigo-300 transition"
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                          Retry
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Retry Modal */}
      {selectedJob && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in">
          <div className="bg-white dark:bg-slate-900 rounded-2xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400">
              <RotateCcw className="w-5 h-5" />
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">Retry Ingestion Job</h3>
            </div>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Job <code className="font-mono bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">{selectedJob.id.substring(0, 8)}</code> will be reset to <span className="font-semibold text-slate-700 dark:text-slate-300">PENDING</span> for worker claiming.
            </p>
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Audit Reason (Mandatory)
              </label>
              <textarea
                value={retryReason}
                onChange={(e) => setRetryReason(e.target.value)}
                placeholder="e.g., Transient timeout resolved; re-triggering extraction."
                rows={3}
                className="w-full text-sm rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-3 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setSelectedJob(null)}
                className="px-4 py-2 text-sm font-semibold text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-xl"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={retryLoading || !retryReason.trim()}
                onClick={handleRetry}
                className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl shadow-sm transition"
              >
                {retryLoading ? 'Queuing...' : 'Confirm Retry'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reprocess Document Modal */}
      {showReprocessModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in">
          <form onSubmit={handleReprocess} className="bg-white dark:bg-slate-900 rounded-2xl p-6 max-w-md w-full border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400">
              <Layers className="w-5 h-5" />
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">Reprocess with New Pipeline</h3>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Re-extracts and chunks the document into a shadow pipeline version. The active pointer switches atomically without search downtime.
            </p>
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Document UUID
              </label>
              <input
                type="text"
                required
                value={reprocessDocId}
                onChange={(e) => setReprocessDocId(e.target.value)}
                placeholder="e.g. 3fa85f64-5717-4562-b3fc-2c963f66afa6"
                className="w-full text-sm rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2.5 font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                New Pipeline Version
              </label>
              <input
                type="text"
                required
                value={reprocessPipelineVer}
                onChange={(e) => setReprocessPipelineVer(e.target.value)}
                placeholder="v2"
                className="w-full text-sm rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2.5 font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                Audit Reason
              </label>
              <textarea
                required
                value={reprocessReason}
                onChange={(e) => setReprocessReason(e.target.value)}
                placeholder="Upgrading to new chunking tokenizer parameters."
                rows={2}
                className="w-full text-sm rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowReprocessModal(false)}
                className="px-4 py-2 text-sm font-semibold text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-xl"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={retryLoading}
                className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl shadow-sm transition"
              >
                {retryLoading ? 'Submitting...' : 'Queue Reprocess'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
