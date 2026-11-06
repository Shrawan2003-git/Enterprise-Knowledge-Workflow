import React, { useState } from 'react';
import { documentApi } from '../api/documentApi';
import { UploadCloud, AlertCircle, X } from 'lucide-react';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const UploadModal: React.FC<UploadModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [classification, setClassification] = useState('INTERNAL');
  const [changeNote, setChangeNote] = useState('Initial upload');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      if (!selected.name.toLowerCase().endsWith('.pdf')) {
        setError('Only PDF documents are supported in Phase 2');
        return;
      }
      setFile(selected);
      if (!title) {
        setTitle(selected.name.replace(/\.[^/.]+$/, ""));
      }
      setError(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a PDF document');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await documentApi.uploadDocument(file, {
        title,
        description,
        classification,
        changeNote
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      setError(err.message || 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="w-full max-w-xl rounded-2xl border border-slate-700 bg-slate-900 p-6 shadow-2xl space-y-6">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <h3 className="text-xl font-bold text-white">Upload Governed Document</h3>
          <button onClick={onClose} className="rounded-lg p-1.5 text-slate-400 hover:text-white hover:bg-slate-800">
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-rose-500/10 p-4 text-sm text-rose-400 border border-rose-500/20">
            <AlertCircle className="h-5 w-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Drag & Drop File Zone */}
          <div className="relative rounded-xl border-2 border-dashed border-slate-700 hover:border-teal-500 p-6 text-center transition-colors">
            <input
              type="file"
              accept=".pdf"
              onChange={handleFileChange}
              className="absolute inset-0 cursor-pointer opacity-0"
            />
            <div className="flex flex-col items-center justify-center space-y-2">
              <UploadCloud className="h-10 w-10 text-teal-400" />
              <p className="text-sm font-medium text-slate-300">
                {file ? file.name : 'Click or drag PDF document to upload'}
              </p>
              <p className="text-xs text-slate-500">Supported: PDF files up to 50MB (sniffed magic bytes validation)</p>
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Title</label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 p-2.5 text-sm text-white focus:border-teal-500 focus:outline-none"
              placeholder="Operational Policy 01"
            />
          </div>

          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Description</label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 p-2.5 text-sm text-white focus:border-teal-500 focus:outline-none"
              placeholder="High-level summary of procedures..."
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Classification</label>
              <select
                value={classification}
                onChange={(e) => setClassification(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 p-2.5 text-sm text-white focus:border-teal-500 focus:outline-none"
              >
                <option value="PUBLIC">PUBLIC</option>
                <option value="INTERNAL">INTERNAL</option>
                <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                <option value="RESTRICTED">RESTRICTED</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Revision Note</label>
              <input
                type="text"
                value={changeNote}
                onChange={(e) => setChangeNote(e.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 p-2.5 text-sm text-white focus:border-teal-500 focus:outline-none"
                placeholder="Initial draft"
              />
            </div>
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-300 hover:bg-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !file}
              className="rounded-lg bg-teal-600 px-5 py-2 text-sm font-semibold text-white shadow-lg hover:bg-teal-500 disabled:opacity-50"
            >
              {loading ? 'Uploading & Verifying...' : 'Publish Version'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
