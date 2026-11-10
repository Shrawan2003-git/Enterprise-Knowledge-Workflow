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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-xl neon-radiant-card rounded-3xl p-7 space-y-6">
        <div className="flex items-center justify-between border-b border-blue-400/20 pb-4">
          <h3 className="text-xl font-black text-white">Upload Governed Document</h3>
          <button onClick={onClose} className="pill-badge rounded-xl p-2 text-slate-400 hover:text-white transition-colors">
            <X className="h-4 w-4" />
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-2xl pill-badge text-rose-300 border-rose-500/40 p-3.5 text-xs font-bold">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Drag & Drop File Zone */}
          <div className="relative rounded-2xl border-2 border-dashed border-blue-500/40 hover:border-blue-400 p-6 text-center transition-colors dark-card">
            <input
              type="file"
              accept=".pdf"
              onChange={handleFileChange}
              className="absolute inset-0 cursor-pointer opacity-0"
            />
            <div className="flex flex-col items-center justify-center space-y-2">
              <UploadCloud className="h-10 w-10 text-blue-400" />
              <p className="text-sm font-bold text-white">
                {file ? file.name : 'Click or drag PDF document to upload'}
              </p>
              <p className="text-xs text-slate-400 font-medium">Supported: PDF files up to 50MB (sniffed magic bytes validation)</p>
            </div>
          </div>

          <div>
            <label className="text-xs font-bold uppercase tracking-wider text-slate-300">Title</label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="mt-1.5 block w-full rounded-2xl clay-input px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none"
              placeholder="Operational Policy 01"
            />
          </div>

          <div>
            <label className="text-xs font-bold uppercase tracking-wider text-slate-300">Description</label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mt-1.5 block w-full rounded-2xl clay-input px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none"
              placeholder="High-level summary of procedures..."
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300">Classification</label>
              <select
                value={classification}
                onChange={(e) => setClassification(e.target.value)}
                className="mt-1.5 block w-full rounded-2xl clay-input px-3 py-2.5 text-sm text-white focus:outline-none"
              >
                <option value="PUBLIC" className="bg-slate-900 text-slate-200">PUBLIC</option>
                <option value="INTERNAL" className="bg-slate-900 text-slate-200">INTERNAL</option>
                <option value="CONFIDENTIAL" className="bg-slate-900 text-slate-200">CONFIDENTIAL</option>
                <option value="RESTRICTED" className="bg-slate-900 text-slate-200">RESTRICTED</option>
              </select>
            </div>
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300">Revision Note</label>
              <input
                type="text"
                value={changeNote}
                onChange={(e) => setChangeNote(e.target.value)}
                className="mt-1.5 block w-full rounded-2xl clay-input px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="Initial draft"
              />
            </div>
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-slate-700/40">
            <button
              type="button"
              onClick={onClose}
              className="pill-badge px-4 py-2 text-xs font-bold text-slate-300 hover:text-white transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !file}
              className="btn-electric px-5 py-2 text-xs font-bold text-white disabled:opacity-50 transition-all"
            >
              {loading ? 'Uploading & Verifying...' : 'Publish Version'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
