import React, { useEffect, useState } from 'react';
import { documentApi } from '../api/documentApi';
import { DocumentDetailDto, VersionDto } from '../api/documentTypes';
import { SubmitReviewModal } from './SubmitReviewModal';
import { ArrowLeft, Download, History, AlertTriangle, Send } from 'lucide-react';

interface DocumentDetailViewProps {
  documentId: string;
  onBack: () => void;
  canEdit?: boolean;
}

export const DocumentDetailView: React.FC<DocumentDetailViewProps> = ({ documentId, onBack }) => {
  const [doc, setDoc] = useState<DocumentDetailDto | null>(null);
  const [versions, setVersions] = useState<VersionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitReviewOpen, setIsSubmitReviewOpen] = useState(false);

  useEffect(() => {
    loadData();
  }, [documentId]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [detail, vers] = await Promise.all([
        documentApi.getDocument(documentId),
        documentApi.listVersions(documentId)
      ]);
      setDoc(detail);
      setVersions(vers);
    } catch (err: any) {
      setError(err.message || 'Failed to load document');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (versionNumber?: number) => {
    try {
      const blob = await documentApi.downloadDocument(documentId, versionNumber);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${doc?.title || 'document'}_v${versionNumber || doc?.currentVersion?.versionNumber || 1}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err: any) {
      alert('Download failed: ' + err.message);
    }
  };

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="h-8 w-48 rounded-2xl clay-card"></div>
        <div className="h-44 rounded-3xl clay-card"></div>
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="rounded-3xl clay-card-elevated p-8 text-center clay-badge-rose space-y-3">
        <AlertTriangle className="h-8 w-8 mx-auto" />
        <p className="font-bold text-base">{error || 'Document not accessible'}</p>
        <button onClick={onBack} className="text-xs underline text-slate-300 font-semibold">Return to library</button>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <button
          onClick={onBack}
          className="clay-btn-secondary inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-slate-300 hover:text-white transition-all w-fit"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Knowledge Library
        </button>

        <div className="flex items-center gap-3">
          {(doc.status === 'DRAFT' || doc.status === 'CHANGES_REQUESTED') && (
            <button
              onClick={() => setIsSubmitReviewOpen(true)}
              className="btn-electric inline-flex items-center gap-2 px-4 py-2 text-xs font-bold text-white transition-all"
            >
              <Send className="h-4 w-4" />
              Submit for Review
            </button>
          )}
          <button
            onClick={() => handleDownload()}
            className="pill-badge px-4 py-2 text-xs font-bold text-slate-200 hover:text-white transition-all"
          >
            <Download className="h-4 w-4" />
            Download Current (v{doc.currentVersion?.versionNumber || 1})
          </button>
        </div>
      </div>

      {/* Main Metadata Card */}
      <div className="neon-radiant-card rounded-3xl p-8 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-2.5">
              <span className="pill-badge text-emerald-300 border-emerald-500/40 bg-emerald-950/40 px-3 py-0.5 text-xs font-bold uppercase">
                {doc.status}
              </span>
              <span className="pill-badge-active px-3 py-0.5 text-xs font-bold uppercase">
                {doc.classification}
              </span>
            </div>
            <h1 className="text-3xl font-black text-white tracking-tight">{doc.title}</h1>
          </div>

          <div className="text-right text-xs text-slate-400 space-y-1 font-medium">
            <div>Created: {new Date(doc.createdAt).toLocaleDateString()}</div>
            <div>Optimistic Lock Version: {doc.version}</div>
          </div>
        </div>

        <p className="text-slate-300 text-sm leading-relaxed max-w-3xl font-normal">
          {doc.description || 'No description provided.'}
        </p>

        {doc.currentVersion && (
          <div className="clay-card rounded-2xl p-5 grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
            <div>
              <span className="text-slate-400 font-semibold block">Checksum (SHA-256)</span>
              <span className="font-mono text-slate-200 font-bold truncate block mt-1" title={doc.currentVersion.checksum}>
                {doc.currentVersion.checksum.substring(0, 16)}...
              </span>
            </div>
            <div>
              <span className="text-slate-400 font-semibold block">File Size</span>
              <span className="text-slate-200 font-bold block mt-1">
                {(doc.currentVersion.sizeBytes / 1024).toFixed(1)} KB
              </span>
            </div>
            <div>
              <span className="text-slate-400 font-semibold block">MIME Type</span>
              <span className="text-slate-200 font-bold block mt-1">{doc.currentVersion.mimeType}</span>
            </div>
            <div>
              <span className="text-slate-400 font-semibold block">Ingestion Pipeline</span>
              <span className="text-teal-400 font-bold block mt-1">{doc.currentVersion.processingStatus}</span>
            </div>
          </div>
        )}
      </div>

      {/* Version History Table */}
      <div className="clay-card rounded-3xl p-6 space-y-4">
        <div className="flex items-center gap-2.5 text-white font-black text-lg">
          <History className="h-5 w-5 text-teal-400" />
          <h3>Immutable Version History</h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase tracking-wider text-slate-400 border-b border-slate-700/40">
              <tr>
                <th className="px-4 py-3 font-bold">Version</th>
                <th className="px-4 py-3 font-bold">Uploaded Date</th>
                <th className="px-4 py-3 font-bold">Checksum</th>
                <th className="px-4 py-3 font-bold">Change Note</th>
                <th className="px-4 py-3 font-bold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/30">
              {versions.map((v) => (
                <tr key={v.id} className="hover:bg-slate-800/30 transition-colors">
                  <td className="px-4 py-3.5 font-mono font-bold text-white">v{v.versionNumber}</td>
                  <td className="px-4 py-3.5 text-slate-400">{new Date(v.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3.5 font-mono text-xs text-slate-400">{v.checksum.substring(0, 12)}...</td>
                  <td className="px-4 py-3.5 text-slate-200 font-medium">{v.changeNote || '-'}</td>
                  <td className="px-4 py-3.5 text-right">
                    <button
                      onClick={() => handleDownload(v.versionNumber)}
                      className="clay-btn-secondary inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-teal-400 hover:text-white transition-all font-bold"
                    >
                      <Download className="h-3.5 w-3.5" />
                      Download
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {/* Submit for Review Modal */}
      {isSubmitReviewOpen && (
        <SubmitReviewModal
          documentId={doc.id}
          documentTitle={doc.title}
          isOpen={true}
          onClose={() => setIsSubmitReviewOpen(false)}
          onSuccess={() => loadData()}
        />
      )}
    </div>
  );
};
