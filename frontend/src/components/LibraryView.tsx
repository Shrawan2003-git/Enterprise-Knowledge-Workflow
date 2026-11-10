import React, { useState } from 'react';
import { DocumentSummaryDto } from '../api/documentTypes';
import { searchApi, SearchResultDto } from '../api/searchApi';
import { ingestionApi, ChunkCitationDto } from '../api/ingestionApi';
import { SubmitReviewModal } from './SubmitReviewModal';
import { 
  FileText, Clock, Plus, Search, X, Send, Sparkles, 
  BookOpen, ChevronRight, RefreshCw
} from 'lucide-react';

interface LibraryViewProps {
  documents: DocumentSummaryDto[];
  loading: boolean;
  onSelectDoc: (id: string) => void;
  onOpenUpload: () => void;
  canUpload: boolean;
  onRefreshDocs?: () => void;
}

export const LibraryView: React.FC<LibraryViewProps> = ({
  documents,
  loading,
  onSelectDoc,
  onOpenUpload,
  canUpload,
  onRefreshDocs,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedClassification, setSelectedClassification] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SearchResultDto[] | null>(null);
  const [chunkResults, setChunkResults] = useState<ChunkCitationDto[] | null>(null);
  const [searchMode, setSearchMode] = useState<'DOCUMENTS' | 'CHUNKS'>('DOCUMENTS');
  const [searching, setSearching] = useState(false);

  // Submit review state
  const [reviewModalDoc, setReviewModalDoc] = useState<{ id: string; title: string } | null>(null);

  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!searchQuery.trim() && !selectedClassification && !selectedStatus) {
      setSearchResults(null);
      setChunkResults(null);
      return;
    }

    setSearching(true);
    try {
      if (searchMode === 'CHUNKS') {
        const chunks = await ingestionApi.searchChunks(searchQuery.trim(), 15);
        setChunkResults(chunks);
        setSearchResults(null);
      } else {
        const res = await searchApi.search({
          q: searchQuery.trim() || undefined,
          classification: selectedClassification || undefined,
          status: selectedStatus || undefined,
          page: 0,
          size: 50,
        });
        setSearchResults(res.content);
        setChunkResults(null);
      }
    } catch {
      setSearchResults([]);
      setChunkResults([]);
    } finally {
      setSearching(false);
    }
  };

  const handleClearFilters = () => {
    setSearchQuery('');
    setSelectedClassification('');
    setSelectedStatus('');
    setSearchResults(null);
    setChunkResults(null);
  };

  const getClassificationBadge = (classification: string) => {
    switch (classification.toUpperCase()) {
      case 'PUBLIC':
        return 'pill-badge text-emerald-400 border-emerald-500/30';
      case 'INTERNAL':
        return 'pill-badge-active text-blue-200';
      case 'CONFIDENTIAL':
        return 'pill-badge text-amber-400 border-amber-500/30';
      case 'RESTRICTED':
        return 'pill-badge text-rose-400 border-rose-500/30';
      default:
        return 'pill-badge text-slate-300';
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return 'pill-badge text-emerald-300 border-emerald-500/40 bg-emerald-950/40';
      case 'IN_REVIEW':
        return 'pill-badge text-amber-300 border-amber-500/40 bg-amber-950/40';
      case 'CHANGES_REQUESTED':
        return 'pill-badge text-amber-300 border-amber-500/40 bg-amber-950/40';
      case 'REJECTED':
        return 'pill-badge text-rose-300 border-rose-500/40 bg-rose-950/40';
      case 'ARCHIVED':
        return 'pill-badge text-slate-400';
      default:
        return 'pill-badge text-slate-300';
    }
  };

  const isSearchActive = searchResults !== null || chunkResults !== null;
  const displayItems = searchResults !== null ? searchResults : documents;

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="dark-card p-6 md:p-8 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-5">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-extrabold tracking-tight text-white">
              Governed Document Library
            </h2>
            <span className="pill-badge-active text-xs font-bold px-3 py-1">Zero-Trust ACL Active</span>
          </div>
          <p className="text-xs md:text-sm text-slate-400 mt-1">
            Full-text indexed documents with strict access governance, page-aware chunk citations, and immutable versions.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          {onRefreshDocs && (
            <button
              onClick={onRefreshDocs}
              className="pill-badge px-3.5 py-2.5 text-xs font-bold text-slate-300 hover:text-white flex items-center gap-1.5"
              title="Refresh Library"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              <span>Refresh</span>
            </button>
          )}
          {canUpload && (
            <button
              onClick={onOpenUpload}
              className="btn-electric px-5 py-2.5 text-xs font-bold text-white shadow-lg flex items-center gap-2"
            >
              <Plus className="h-4 w-4" />
              <span>Upload Document</span>
            </button>
          )}
        </div>
      </div>

      {/* Search Bar & Scope Filters */}
      <div className="dark-card p-5 space-y-4">
        <div className="flex items-center gap-2.5 text-xs">
          <span className="font-bold text-slate-400 uppercase tracking-wider mr-1">Search Scope:</span>
          <button
            onClick={() => {
              setSearchMode('DOCUMENTS');
              setChunkResults(null);
            }}
            className={`px-4 py-1.5 text-xs font-bold rounded-xl transition ${
              searchMode === 'DOCUMENTS'
                ? 'pill-badge-active text-white'
                : 'pill-badge text-slate-400 hover:text-white'
            }`}
          >
            Document Level
          </button>
          <button
            onClick={() => {
              setSearchMode('CHUNKS');
              setSearchResults(null);
            }}
            className={`px-4 py-1.5 text-xs font-bold rounded-xl transition flex items-center gap-1.5 ${
              searchMode === 'CHUNKS'
                ? 'pill-badge-active text-white'
                : 'pill-badge text-slate-400 hover:text-white'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            Page-Aware Chunks & Citations
          </button>
        </div>

        <form
          onSubmit={handleSearch}
          className="flex flex-col md:flex-row gap-3 items-stretch md:items-center"
        >
          <div className="relative flex-1">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <input
              type="text"
              placeholder={
                searchMode === 'CHUNKS'
                  ? 'Search paragraph text across extracted pages with citations (e.g. "security compliance", "indemnification")...'
                  : 'Search documents by title, description, or keyword (PostgreSQL tsvector FTS)...'
              }
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="clay-input w-full py-3 pl-11 pr-4 text-sm text-white placeholder-slate-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-2">
            {searchMode === 'DOCUMENTS' && (
              <>
                <select
                  value={selectedClassification}
                  onChange={(e) => setSelectedClassification(e.target.value)}
                  className="pill-badge px-3 py-2.5 text-xs font-bold text-white focus:outline-none cursor-pointer bg-slate-900"
                >
                  <option value="" className="bg-slate-900">All Classifications</option>
                  <option value="PUBLIC" className="bg-slate-900">Public</option>
                  <option value="INTERNAL" className="bg-slate-900">Internal</option>
                  <option value="CONFIDENTIAL" className="bg-slate-900">Confidential</option>
                  <option value="RESTRICTED" className="bg-slate-900">Restricted</option>
                </select>

                <select
                  value={selectedStatus}
                  onChange={(e) => setSelectedStatus(e.target.value)}
                  className="pill-badge px-3 py-2.5 text-xs font-bold text-white focus:outline-none cursor-pointer bg-slate-900"
                >
                  <option value="" className="bg-slate-900">All Statuses</option>
                  <option value="DRAFT" className="bg-slate-900">Draft</option>
                  <option value="IN_REVIEW" className="bg-slate-900">In Review</option>
                  <option value="APPROVED" className="bg-slate-900">Approved</option>
                  <option value="CHANGES_REQUESTED" className="bg-slate-900">Changes Requested</option>
                  <option value="REJECTED" className="bg-slate-900">Rejected</option>
                </select>
              </>
            )}

            <button
              type="submit"
              disabled={searching}
              className="btn-electric px-5 py-2.5 text-xs font-bold text-white disabled:opacity-50"
            >
              {searching ? 'Searching...' : 'Search'}
            </button>

            {(searchQuery || selectedClassification || selectedStatus || isSearchActive) && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="pill-badge p-2.5 text-slate-400 hover:text-white"
                title="Clear filters"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>
        </form>
      </div>

      {/* Search status header */}
      {isSearchActive && (
        <div className="flex items-center justify-between text-xs text-slate-400 px-1">
          <span className="flex items-center gap-1.5 text-teal-400 font-bold">
            <Sparkles className="h-3.5 w-3.5" />
            {searchMode === 'CHUNKS'
              ? `Found ${chunkResults?.length || 0} matching citation chunk${chunkResults?.length === 1 ? '' : 's'}`
              : `Found ${searchResults?.length || 0} matching document${searchResults?.length === 1 ? '' : 's'}`}
          </span>
          <button
            onClick={handleClearFilters}
            className="hover:underline text-slate-400 font-semibold"
          >
            Reset search
          </button>
        </div>
      )}

      {/* CHUNKS CITATION SEARCH RESULTS */}
      {searchMode === 'CHUNKS' && chunkResults !== null && (
        <div className="space-y-4">
          {chunkResults.length === 0 ? (
            <div className="clay-card p-12 text-center border border-dashed border-slate-700/50">
              <BookOpen className="mx-auto h-12 w-12 text-slate-500" />
              <h3 className="mt-4 text-base font-bold text-white">No matching citation chunks</h3>
              <p className="mt-1 text-sm text-slate-400">
                Try querying specific policy terminology or ensuring target documents are in APPROVED and READY status.
              </p>
            </div>
          ) : (
            chunkResults.map((chunk) => (
              <div
                key={chunk.chunkId}
                onClick={() => onSelectDoc(chunk.documentId)}
                className="clay-card-elevated p-5 space-y-3 hover:scale-[1.01] transition-transform cursor-pointer border border-slate-700/20"
              >
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-teal-400">{chunk.documentTitle}</span>
                    <span className="font-mono text-slate-500">v{chunk.versionNumber}</span>
                  </div>
                  <span className="clay-badge-cyan inline-flex items-center gap-1 px-3 py-1 font-mono text-xs font-bold">
                    <BookOpen className="w-3.5 h-3.5" />
                    Page {chunk.pageStart === chunk.pageEnd ? chunk.pageStart : `${chunk.pageStart}–${chunk.pageEnd}`}
                  </span>
                </div>

                <div className="text-xs text-slate-300 font-serif leading-relaxed clay-input p-3 border-l-4 border-teal-400">
                  "...{chunk.snippet}..."
                </div>

                <div className="flex items-center justify-between text-[11px] text-slate-400">
                  <span>Relevance score: <strong className="text-teal-400">{chunk.relevanceScore.toFixed(1)}</strong></span>
                  <span className="inline-flex items-center gap-1 text-teal-400 font-bold hover:translate-x-0.5 transition-transform">
                    View in document <ChevronRight className="w-3 h-3" />
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Grid of Documents (Document Mode) */}
      {searchMode === 'DOCUMENTS' && (
        loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2, 3].map((i) => (
              <div key={i} className="clay-card h-48 p-6 animate-pulse space-y-4">
                <div className="h-6 w-3/4 rounded-xl bg-slate-800"></div>
                <div className="h-4 w-full rounded-xl bg-slate-800/60"></div>
                <div className="h-4 w-1/2 rounded-xl bg-slate-800/60"></div>
              </div>
            ))}
          </div>
        ) : displayItems.length === 0 ? (
          <div className="clay-card p-12 text-center border border-dashed border-slate-700/50">
            <FileText className="mx-auto h-12 w-12 text-slate-500" />
            <h3 className="mt-4 text-base font-bold text-white">
              {isSearchActive ? 'No matching documents found' : 'No documents accessible'}
            </h3>
            <p className="mt-1 text-sm text-slate-400">
              {isSearchActive
                ? 'Try modifying your search query or loosening the classification/status filters.'
                : 'You currently do not have access to any governed documents in this workspace.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {displayItems.map((doc: any) => {
              const hasSnippet = !!doc.snippet;
              const isDraft = doc.status === 'DRAFT' || doc.status === 'CHANGES_REQUESTED';
              const isApproved = doc.status === 'APPROVED';
              return (
                <div
                  key={doc.id}
                  className={`${isApproved ? 'neon-radiant-card' : 'dark-card'} p-6 space-y-4 hover:scale-[1.01] transition-transform flex flex-col justify-between`}
                >
                  <div className="space-y-3 cursor-pointer" onClick={() => onSelectDoc(doc.id)}>
                    <div className="flex items-center justify-between gap-2">
                      <span className={`px-2.5 py-0.5 text-[11px] font-bold ${getClassificationBadge(doc.classification)}`}>
                        {doc.classification}
                      </span>
                      <span className={`px-2.5 py-0.5 text-[11px] font-bold ${getStatusBadge(doc.status)}`}>
                        {doc.status}
                      </span>
                    </div>

                    <h3 className="text-base font-bold text-white group-hover:text-blue-400 transition-colors line-clamp-1 mt-2">
                      {doc.title}
                    </h3>

                    {hasSnippet ? (
                      <div
                        className="text-xs text-slate-300 clay-input p-3 font-serif leading-relaxed"
                        dangerouslySetInnerHTML={{ __html: doc.snippet }}
                      />
                    ) : (
                      <p className="text-xs text-slate-300 line-clamp-2">
                        {doc.description || 'No document description provided.'}
                      </p>
                    )}
                  </div>

                  <div className="mt-4 pt-4 border-t border-slate-700/40 flex items-center justify-between text-xs text-slate-400">
                    <span className="flex items-center gap-1 font-mono text-blue-400 font-bold">
                      v{doc.currentVersionNumber || 1}.0
                    </span>

                    <div className="flex items-center gap-2">
                      {isDraft && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setReviewModalDoc({ id: doc.id, title: doc.title });
                          }}
                          className="pill-badge px-3 py-1.5 text-xs font-bold text-blue-300 hover:text-white flex items-center gap-1"
                        >
                          <Send className="h-3 w-3" />
                          <span>Submit Review</span>
                        </button>
                      )}
                      <span className="flex items-center gap-1 text-[11px]">
                        <Clock className="h-3.5 w-3.5" />
                        {doc.createdAt ? new Date(doc.createdAt).toLocaleDateString() : 'Active'}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )
      )}

      {/* Submit for Review Modal */}
      {reviewModalDoc && (
        <SubmitReviewModal
          documentId={reviewModalDoc.id}
          documentTitle={reviewModalDoc.title}
          isOpen={true}
          onClose={() => setReviewModalDoc(null)}
          onSuccess={() => {
            onRefreshDocs?.();
            handleSearch();
          }}
        />
      )}
    </div>
  );
};
