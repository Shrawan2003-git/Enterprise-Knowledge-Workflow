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
          <span className="clay-badge-cyan inline-flex items-center gap-1.5 px-3 py-0.5 text-xs font-bold animate-pulse">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-400"></span>
            RUNNING
          </span>
        );
      case 'SUCCEEDED':
        return (
          <span className="clay-badge-emerald inline-flex items-center gap-1.5 px-3 py-0.5 text-xs font-bold">
            <CheckCircle2 className="w-3.5 h-3.5" />
            SUCCEEDED
          </span>
        );
      case 'FAILED':
        return (
          <span className="clay-badge-rose inline-flex items-center gap-1.5 px-3 py-0.5 text-xs font-bold">
            <ShieldAlert className="w-3.5 h-3.5" />
            FAILED
          </span>
        );
      case 'RETRY_SCHEDULED':
        return (
          <span className="clay-badge-amber inline-flex items-center gap-1.5 px-3 py-0.5 text-xs font-bold">
            <RotateCcw className="w-3.5 h-3.5" />
            RETRY PENDING
          </span>
        );
      default:
        return (
          <span className="clay-badge-cyan inline-flex items-center gap-1.5 px-3 py-0.5 text-xs font-bold">
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
    <div className="space-y-6 animate-in fade-in duration-300">
      {/* Header and KPI summary */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 dark-card p-6 rounded-3xl">
        <div>
          <div className="flex items-center gap-2.5">
            <Cpu className="w-6 h-6 text-blue-400" />
            <h2 className="text-xl font-black text-white">Ingestion Pipeline Engine</h2>
          </div>
          <p className="text-sm text-slate-400 mt-1">
            Governed asynchronous background workers, PDF parsing, chunk indexing, and atomic reprocessing.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowReprocessModal(true)}
            className="btn-electric inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-white transition-all"
          >
            <Layers className="w-4 h-4" />
            Reprocess Pipeline
          </button>
          <button
            onClick={loadJobs}
            disabled={loading}
            className="pill-badge inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-slate-200 transition-all hover:text-white"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="p-5 rounded-3xl dark-card">
          <div className="text-xs font-bold text-slate-400 uppercase tracking-wider">Active Workers</div>
          <div className="text-2xl font-black text-white mt-1">{counts.running}</div>
        </div>
        <div className="p-5 rounded-3xl dark-card">
          <div className="text-xs font-bold text-slate-400 uppercase tracking-wider">Queued Jobs</div>
          <div className="text-2xl font-black text-white mt-1">{counts.pending}</div>
        </div>
        <div className="p-5 rounded-3xl dark-card">
          <div className="text-xs font-bold text-emerald-400 uppercase tracking-wider">Indexed Ready</div>
          <div className="text-2xl font-black text-emerald-400 mt-1">{counts.succeeded}</div>
        </div>
        <div className="p-5 rounded-3xl dark-card">
          <div className="text-xs font-bold text-rose-400 uppercase tracking-wider">Dead Letter Failures</div>
          <div className="text-2xl font-black text-rose-400 mt-1">{counts.failed}</div>
        </div>
      </div>

      {/* Feedback alerts */}
      {errorMsg && (
        <div className="p-4 rounded-2xl clay-badge-rose text-xs font-bold flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}
      {successMsg && (
        <div className="p-4 rounded-2xl clay-badge-emerald text-xs font-bold flex items-center gap-3">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        {['ALL', 'PENDING', 'RUNNING', 'FAILED', 'RETRY_SCHEDULED', 'SUCCEEDED'].map((st) => (
          <button
            key={st}
            onClick={() => setStatusFilter(st)}
            className={`px-4 py-2 rounded-2xl text-xs font-bold transition-all ${
              statusFilter === st
                ? 'clay-pill-active text-white'
                : 'clay-btn-secondary text-slate-400 hover:text-white'
            }`}
          >
            {st}
          </button>
        ))}
      </div>

      {/* Jobs Table */}
      <div className="clay-card rounded-3xl overflow-hidden p-6">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase tracking-wider text-slate-400 border-b border-slate-700/40">
              <tr>
                <th className="px-5 py-3 font-bold">Job ID / Pipeline</th>
                <th className="px-5 py-3 font-bold">Status</th>
                <th className="px-5 py-3 font-bold">Attempts</th>
                <th className="px-5 py-3 font-bold">Worker Lock</th>
                <th className="px-5 py-3 font-bold">Error Diagnostics</th>
                <th className="px-5 py-3 font-bold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/30">
              {jobs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-slate-500 font-medium italic">
                    No ingestion jobs matching filter "{statusFilter}".
                  </td>
                </tr>
              ) : (
                jobs.map((job) => (
                  <tr key={job.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="px-5 py-4">
                      <div className="font-mono text-xs font-bold text-white">
                        {job.id.substring(0, 8)}...
                      </div>
                      <div className="inline-flex items-center gap-1 text-xs text-teal-400 mt-1 font-semibold">
                        <Layers className="w-3 h-3" />
                        {job.pipelineVersion}
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      {getStatusBadge(job.status)}
                    </td>
                    <td className="px-5 py-4">
                      <span className="font-bold text-white">
                        {job.attempts}
                      </span>
                      <span className="text-slate-400 text-xs"> / {job.maxAttempts}</span>
                    </td>
                    <td className="px-5 py-4">
                      {job.lockedBy ? (
                        <span className="inline-flex items-center gap-1 font-mono text-xs text-slate-200 clay-badge-cyan px-2.5 py-0.5 font-bold">
                          <Cpu className="w-3 h-3 text-sky-400" />
                          {job.lockedBy}
                        </span>
                      ) : (
                        <span className="text-slate-500 text-xs">—</span>
                      )}
                    </td>
                    <td className="px-5 py-4 max-w-xs">
                      {job.errorCode ? (
                        <div>
                          <span className="inline-block text-xs font-mono font-bold px-2 py-0.5 rounded clay-badge-rose">
                            {job.errorCode}
                          </span>
                          {job.errorMessage && (
                            <p className="text-xs text-slate-400 mt-1 truncate font-medium" title={job.errorMessage}>
                              {job.errorMessage}
                            </p>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-emerald-400 font-bold">None</span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-right">
                      {job.status === 'FAILED' && (
                        <button
                          onClick={() => {
                            setSelectedJob(job);
                            setRetryReason('');
                          }}
                          className="clay-btn-secondary inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-teal-400 hover:text-white transition-all"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in">
          <div className="clay-card-elevated rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center gap-2 text-teal-400 font-bold">
              <RotateCcw className="w-5 h-5" />
              <h3 className="text-lg font-black text-white">Retry Ingestion Job</h3>
            </div>
            <p className="text-sm text-slate-300">
              Job <code className="font-mono clay-badge-cyan px-1.5 py-0.5 text-xs">{selectedJob.id.substring(0, 8)}</code> will be reset to <span className="font-bold text-white">PENDING</span> for worker claiming.
            </p>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-300 mb-1">
                Audit Reason (Mandatory)
              </label>
              <textarea
                value={retryReason}
                onChange={(e) => setRetryReason(e.target.value)}
                placeholder="e.g., Transient timeout resolved; re-triggering extraction."
                rows={3}
                className="w-full text-sm rounded-2xl clay-input p-3 focus:outline-none"
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setSelectedJob(null)}
                className="clay-btn-secondary px-4 py-2 text-xs font-bold text-slate-300 hover:text-white transition-all"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={retryLoading || !retryReason.trim()}
                onClick={handleRetry}
                className="clay-btn-teal px-5 py-2 text-xs font-bold text-white disabled:opacity-50 transition-all"
              >
                {retryLoading ? 'Queuing...' : 'Confirm Retry'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reprocess Document Modal */}
      {showReprocessModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in">
          <form onSubmit={handleReprocess} className="clay-card-elevated rounded-3xl p-6 max-w-md w-full space-y-4">
            <div className="flex items-center gap-2 text-teal-400 font-bold">
              <Layers className="w-5 h-5" />
              <h3 className="text-lg font-black text-white">Reprocess with New Pipeline</h3>
            </div>
            <p className="text-xs text-slate-400">
              Re-extracts and chunks the document into a shadow pipeline version. The active pointer switches atomically without search downtime.
            </p>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-300 mb-1">
                Document UUID
              </label>
              <input
                type="text"
                required
                value={reprocessDocId}
                onChange={(e) => setReprocessDocId(e.target.value)}
                placeholder="e.g. 3fa85f64-5717-4562-b3fc-2c963f66afa6"
                className="w-full text-sm rounded-2xl clay-input p-2.5 font-mono focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-300 mb-1">
                New Pipeline Version
              </label>
              <input
                type="text"
                required
                value={reprocessPipelineVer}
                onChange={(e) => setReprocessPipelineVer(e.target.value)}
                placeholder="v2"
                className="w-full text-sm rounded-2xl clay-input p-2.5 font-mono focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-300 mb-1">
                Audit Reason
              </label>
              <textarea
                required
                value={reprocessReason}
                onChange={(e) => setReprocessReason(e.target.value)}
                placeholder="Upgrading to new chunking tokenizer parameters."
                rows={2}
                className="w-full text-sm rounded-2xl clay-input p-2.5 focus:outline-none"
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setShowReprocessModal(false)}
                className="clay-btn-secondary px-4 py-2 text-xs font-bold text-slate-300 hover:text-white transition-all"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={retryLoading}
                className="clay-btn-teal px-5 py-2 text-xs font-bold text-white disabled:opacity-50 transition-all"
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
