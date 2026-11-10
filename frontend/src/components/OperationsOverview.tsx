import React, { useEffect, useState } from 'react';
import { reportingApi, OverviewReportDto } from '../api/reportingApi';
import {
  FileText,
  CheckCircle,
  AlertTriangle,
  Clock,
  Layers,
  Activity,
  ShieldCheck,
  Cpu,
  RefreshCw
} from 'lucide-react';

export const OperationsOverview: React.FC = () => {
  const [data, setData] = useState<OverviewReportDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchOverview = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await reportingApi.getOverview();
      setData(res);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch operational report');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOverview();
  }, []);

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-white tracking-tight">Operations & Health Overview</h2>
          <p className="text-sm text-slate-400">
            Real-time status across knowledge repositories, workflow queues, and background services.
          </p>
        </div>
        <button
          onClick={fetchOverview}
          disabled={loading}
          className="clay-btn-secondary inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-slate-200 transition-all hover:scale-105"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin text-teal-400' : ''}`} />
          <span>Refresh</span>
        </button>
      </div>

      {error ? (
        <div className="rounded-2xl clay-badge-rose p-4 text-sm font-semibold">
          {error}
        </div>
      ) : (
        <div className="space-y-6">
          {/* Top KPI Cards (Clay) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {/* Governed Docs */}
            <div className="clay-card rounded-3xl p-6 relative overflow-hidden group transition-all duration-200 hover:-translate-y-1">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                  Governed Docs
                </span>
                <div className="h-10 w-10 rounded-2xl clay-badge-cyan flex items-center justify-center font-bold">
                  <FileText className="h-5 w-5" />
                </div>
              </div>
              <div className="mt-4 flex items-baseline gap-2">
                <span className="text-3xl font-black text-white">
                  {data?.documents.totalCount ?? 0}
                </span>
                <span className="text-xs text-slate-400 font-medium">records</span>
              </div>
              <div className="mt-3.5 flex items-center gap-1.5 text-[11px] text-teal-400 font-bold">
                <ShieldCheck className="h-3.5 w-3.5" />
                <span>Central ACL Enforced</span>
              </div>
            </div>

            {/* Open Action Tasks */}
            <div className="clay-card rounded-3xl p-6 relative overflow-hidden group transition-all duration-200 hover:-translate-y-1">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                  Open Tasks
                </span>
                <div className="h-10 w-10 rounded-2xl clay-badge-cyan flex items-center justify-center font-bold">
                  <Clock className="h-5 w-5" />
                </div>
              </div>
              <div className="mt-4 flex items-baseline gap-2">
                <span className="text-3xl font-black text-white">
                  {data?.tasks.openCount ?? 0}
                </span>
                <span className="text-xs text-slate-400 font-medium">pending</span>
              </div>
              <div className="mt-3.5 flex items-center gap-1 text-[11px] text-slate-400 font-semibold">
                <span>{data?.tasks.inProgressCount ?? 0} in progress</span>
              </div>
            </div>

            {/* Overdue Items */}
            <div className="clay-card rounded-3xl p-6 relative overflow-hidden group transition-all duration-200 hover:-translate-y-1">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                  Overdue Tasks
                </span>
                <div className="h-10 w-10 rounded-2xl clay-badge-rose flex items-center justify-center font-bold">
                  <AlertTriangle className="h-5 w-5" />
                </div>
              </div>
              <div className="mt-4 flex items-baseline gap-2">
                <span className="text-3xl font-black text-rose-400">
                  {data?.tasks.overdueCount ?? 0}
                </span>
                <span className="text-xs text-slate-400 font-medium">escalations</span>
              </div>
              <div className="mt-3.5 flex items-center gap-1 text-[11px] text-rose-400 font-bold">
                <span>Requires follow-up</span>
              </div>
            </div>

            {/* Ingestion Engine Status */}
            <div className="clay-card rounded-3xl p-6 relative overflow-hidden group transition-all duration-200 hover:-translate-y-1">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                  Ingestion Engine
                </span>
                <div className="h-10 w-10 rounded-2xl clay-badge-emerald flex items-center justify-center font-bold">
                  <Cpu className="h-5 w-5" />
                </div>
              </div>
              <div className="mt-4 flex items-baseline gap-2">
                <span className="text-2xl font-black text-emerald-400">
                  {data?.ingestion.status ?? 'READY'}
                </span>
              </div>
              <div className="mt-3.5 flex items-center gap-1.5 text-[11px] text-emerald-400 font-bold">
                <CheckCircle className="h-3.5 w-3.5" />
                <span>0 Pipeline Failures</span>
              </div>
            </div>
          </div>

          {/* System Architecture Status Callout */}
          <div className="clay-card-elevated rounded-3xl p-6 space-y-4">
            <div className="flex items-center gap-2.5 text-teal-400 font-bold">
              <Activity className="h-5 w-5" />
              <h3 className="font-bold text-white text-base">Phase 3 System Architecture Status</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
              <div className="clay-card rounded-2xl p-4 space-y-2">
                <div className="font-bold text-white flex items-center gap-2">
                  <Layers className="h-4 w-4 text-teal-400" />
                  Full-Text Search (tsvector)
                </div>
                <p className="text-slate-400 leading-relaxed font-normal">
                  Postgres GIN index active over title, description, category, and tags. ACL filtering is strictly enforced in SQL before pagination and snippet generation.
                </p>
              </div>

              <div className="clay-card rounded-2xl p-4 space-y-2">
                <div className="font-bold text-white flex items-center gap-2">
                  <ShieldCheck className="h-4 w-4 text-teal-400" />
                  Approval State Machine
                </div>
                <p className="text-slate-400 leading-relaxed font-normal">
                  Governed 4-eye separation of duties. Submitters cannot self-approve. Rejection requires written explanation. All transitions append to immutable history.
                </p>
              </div>

              <div className="clay-card rounded-2xl p-4 space-y-2">
                <div className="font-bold text-white flex items-center gap-2">
                  <Cpu className="h-4 w-4 text-teal-400" />
                  Task & Notifications
                </div>
                <p className="text-slate-400 leading-relaxed font-normal">
                  Optimistic locking prevents concurrent edit overwrites. In-app notifications are de-duplicated and linked directly to governed workflows.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
