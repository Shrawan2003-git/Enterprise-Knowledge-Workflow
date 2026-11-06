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
        <div className="h-8 w-48 rounded bg-slate-800"></div>
        <div className="h-40 rounded-xl bg-slate-900 border border-slate-800"></div>
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-6 text-center text-rose-400 space-y-3">
        <AlertTriangle className="h-8 w-8 mx-auto" />
        <p className="font-semibold">{error || 'Document not accessible'}</p>
        <button onClick={onBack} className="text-xs underline text-slate-300">Return to library</button>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <button
          onClick={onBack}
          className="inline-flex items-center gap-2 text-sm text-slate-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Knowledge Library
        </button>

        <div className="flex items-center gap-3">
          {(doc.status === 'DRAFT' || doc.status === 'CHANGES_REQUESTED') && (
            <button
              onClick={() => setIsSubmitReviewOpen(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-teal-500 transition-all"
            >
              <Send className="h-4 w-4" />
              Submit for Review
            </button>
          )}
          <button
            onClick={() => handleDownload()}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-semibold text-slate-200 hover:text-white hover:bg-slate-700 transition-all"
          >
            <Download className="h-4 w-4" />
            Download Current (v{doc.currentVersion?.versionNumber || 1})
          </button>
        </div>
      </div>

      {/* Main Metadata Card */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-8 shadow-xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <span className="rounded-md border border-teal-500/30 bg-teal-500/10 px-2.5 py-0.5 text-xs font-semibold text-teal-300">
                {doc.status}
              </span>
              <span className="rounded-md border border-slate-700 bg-slate-800 px-2.5 py-0.5 text-xs font-semibold text-slate-300">
                {doc.classification}
              </span>
            </div>
            <h1 className="text-3xl font-extrabold text-white tracking-tight">{doc.title}</h1>
          </div>
          <div className="text-right text-xs text-slate-400 space-y-1">
            <div>Created: {new Date(doc.createdAt).toLocaleDateString()}</div>
            <div>Optimistic Lock Version: {doc.version}</div>
          </div>
        </div>

        <p className="text-slate-300 text-sm leading-relaxed max-w-3xl">
          {doc.description || 'No description provided.'}
        </p>

        {doc.currentVersion && (
          <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
            <div>
              <span className="text-slate-500 block">Checksum (SHA-256)</span>
              <span className="font-mono text-slate-300 truncate block" title={doc.currentVersion.checksum}>
                {doc.currentVersion.checksum.substring(0, 16)}...
              </span>
            </div>
            <div>
              <span className="text-slate-500 block">File Size</span>
              <span className="text-slate-300 font-medium">
                {(doc.currentVersion.sizeBytes / 1024).toFixed(1)} KB
              </span>
            </div>
            <div>
              <span className="text-slate-500 block">MIME Type</span>
              <span className="text-slate-300 font-medium">{doc.currentVersion.mimeType}</span>
            </div>
            <div>
              <span className="text-slate-500 block">Ingestion Pipeline</span>
              <span className="text-teal-400 font-medium">{doc.currentVersion.processingStatus}</span>
            </div>
          </div>
        )}
      </div>

      {/* Version History Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 space-y-4">
        <div className="flex items-center gap-2 text-white font-bold text-lg">
          <History className="h-5 w-5 text-teal-400" />
          <h3>Immutable Version History</h3>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-400">
            <thead className="bg-slate-950/50 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-800">
              <tr>
                <th className="px-4 py-3">Version</th>
                <th className="px-4 py-3">Uploaded Date</th>
                <th className="px-4 py-3">Checksum</th>
                <th className="px-4 py-3">Change Note</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {versions.map((v) => (
                <tr key={v.id} className="hover:bg-slate-800/30 transition-colors">
                  <td className="px-4 py-3 font-mono font-semibold text-white">v{v.versionNumber}</td>
                  <td className="px-4 py-3">{new Date(v.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-400">{v.checksum.substring(0, 12)}...</td>
                  <td className="px-4 py-3 text-slate-300">{v.changeNote || '-'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => handleDownload(v.versionNumber)}
                      className="inline-flex items-center gap-1.5 text-xs text-teal-400 hover:text-teal-300 transition-colors"
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
