import React, { useState } from 'react';
import { DocumentSummaryDto } from '../api/documentTypes';
import { searchApi, SearchResultDto } from '../api/searchApi';
import { ingestionApi, ChunkCitationDto } from '../api/ingestionApi';
import { SubmitReviewModal } from './SubmitReviewModal';
import { 
  FileText, Clock, Plus, Search, X, Send, Sparkles, 
  BookOpen, ChevronRight 
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
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'INTERNAL':
        return 'bg-sky-500/10 text-sky-400 border-sky-500/30';
      case 'CONFIDENTIAL':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'RESTRICTED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      default:
        return 'bg-slate-500/10 text-slate-400 border-slate-500/30';
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'IN_REVIEW':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'CHANGES_REQUESTED':
        return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
      case 'REJECTED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      case 'ARCHIVED':
        return 'bg-slate-700/30 text-slate-400 border-slate-700';
      default:
        return 'bg-slate-800 text-slate-300 border-slate-700';
    }
  };

  const isSearchActive = searchResults !== null || chunkResults !== null;
  const displayItems = searchResults !== null ? searchResults : documents;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
            Governed Document Library
          </h2>
          <p className="text-sm text-slate-400 mt-0.5">
            Full-text indexed documents with strict access governance, page-aware chunk citations, and immutable versions.
          </p>
        </div>

        {canUpload && (
          <button
            onClick={onOpenUpload}
            className="inline-flex items-center gap-2 rounded-xl bg-teal-500 hover:bg-teal-400 px-4 py-2 text-sm font-semibold text-slate-950 transition-colors shadow-lg shadow-teal-500/20"
          >
            <Plus className="h-4 w-4" />
            Upload Document
          </button>
        )}
      </div>

      {/* Mode Toggle & Search Bar */}
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Search Scope:</span>
          <div className="inline-flex rounded-xl bg-slate-900 border border-slate-800 p-1">
            <button
              onClick={() => {
                setSearchMode('DOCUMENTS');
                setChunkResults(null);
              }}
              className={`px-3 py-1 text-xs font-semibold rounded-lg transition ${
                searchMode === 'DOCUMENTS'
                  ? 'bg-teal-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Document Level
            </button>
            <button
              onClick={() => {
                setSearchMode('CHUNKS');
                setSearchResults(null);
              }}
              className={`px-3 py-1 text-xs font-semibold rounded-lg transition flex items-center gap-1.5 ${
                searchMode === 'CHUNKS'
                  ? 'bg-teal-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <BookOpen className="w-3.5 h-3.5" />
              Page-Aware Chunks & Citations
            </button>
          </div>
        </div>

        <form
          onSubmit={handleSearch}
          className="rounded-2xl border border-slate-800 bg-slate-900/60 p-3.5 backdrop-blur-sm flex flex-col md:flex-row gap-3 items-stretch md:items-center"
        >
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <input
              type="text"
              placeholder={
                searchMode === 'CHUNKS'
                  ? 'Search paragraph text across extracted pages with citations (e.g. "security compliance", "SLA policy")...'
                  : 'Search documents by title, description, or keyword (PostgreSQL tsvector FTS)...'
              }
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full rounded-xl border border-slate-800 bg-slate-950/80 pl-10 pr-4 py-2 text-sm text-slate-100 placeholder-slate-500 focus:border-teal-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-2">
            {searchMode === 'DOCUMENTS' && (
              <>
                <select
                  value={selectedClassification}
                  onChange={(e) => setSelectedClassification(e.target.value)}
                  className="rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-300 focus:border-teal-500 focus:outline-none"
                >
                  <option value="">All Classifications</option>
                  <option value="PUBLIC">Public</option>
                  <option value="INTERNAL">Internal</option>
                  <option value="CONFIDENTIAL">Confidential</option>
                  <option value="RESTRICTED">Restricted</option>
                </select>

                <select
                  value={selectedStatus}
                  onChange={(e) => setSelectedStatus(e.target.value)}
                  className="rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-300 focus:border-teal-500 focus:outline-none"
                >
                  <option value="">All Statuses</option>
                  <option value="DRAFT">Draft</option>
                  <option value="IN_REVIEW">In Review</option>
                  <option value="APPROVED">Approved</option>
                  <option value="CHANGES_REQUESTED">Changes Requested</option>
                  <option value="REJECTED">Rejected</option>
                </select>
              </>
            )}

            <button
              type="submit"
              disabled={searching}
              className="rounded-xl bg-teal-600 px-4 py-2 text-xs font-semibold text-white shadow-md hover:bg-teal-500 transition-colors"
            >
              {searching ? 'Searching...' : 'Search'}
            </button>

            {(searchQuery || selectedClassification || selectedStatus || isSearchActive) && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="p-2 rounded-xl border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
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
          <span className="flex items-center gap-1.5 text-teal-400 font-medium">
            <Sparkles className="h-3.5 w-3.5" />
            {searchMode === 'CHUNKS'
              ? `Found ${chunkResults?.length || 0} matching citation chunk${chunkResults?.length === 1 ? '' : 's'}`
              : `Found ${searchResults?.length || 0} matching document${searchResults?.length === 1 ? '' : 's'}`}
          </span>
          <button
            onClick={handleClearFilters}
            className="hover:underline text-slate-400"
          >
            Reset search
          </button>
        </div>
      )}

      {/* CHUNKS CITATION SEARCH RESULTS */}
      {searchMode === 'CHUNKS' && chunkResults !== null && (
        <div className="space-y-4">
          {chunkResults.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/30 p-12 text-center">
              <BookOpen className="mx-auto h-12 w-12 text-slate-600" />
              <h3 className="mt-4 text-base font-semibold text-white">No matching citation chunks</h3>
              <p className="mt-1 text-sm text-slate-400">
                Try querying specific policy terminology or ensuring target documents are in APPROVED and READY status.
              </p>
            </div>
          ) : (
            chunkResults.map((chunk) => (
              <div
                key={chunk.chunkId}
                onClick={() => onSelectDoc(chunk.documentId)}
                className="group rounded-xl border border-slate-800 bg-slate-900/60 p-5 backdrop-blur-sm hover:border-teal-500/50 hover:bg-slate-900/90 transition cursor-pointer space-y-3"
              >
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-teal-400">{chunk.documentTitle}</span>
                    <span className="font-mono text-slate-500">v{chunk.versionNumber}</span>
                  </div>
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-slate-800 text-slate-300 font-mono text-[11px] border border-slate-700">
                    <BookOpen className="w-3 h-3 text-teal-400" />
                    Page {chunk.pageStart === chunk.pageEnd ? chunk.pageStart : `${chunk.pageStart}–${chunk.pageEnd}`}
                  </span>
                </div>

                <div className="text-xs text-slate-300 font-serif leading-relaxed bg-slate-950/70 p-3 rounded-lg border border-slate-800/80">
                  "...{chunk.snippet}..."
                </div>

                <div className="flex items-center justify-between text-[11px] text-slate-500">
                  <span>Relevance score: {chunk.relevanceScore.toFixed(1)}</span>
                  <span className="inline-flex items-center gap-1 text-teal-400 group-hover:translate-x-0.5 transition-transform">
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
              <div key={i} className="h-48 rounded-xl border border-slate-800 bg-slate-900/50 p-6 animate-pulse space-y-4">
                <div className="h-6 w-3/4 rounded bg-slate-800"></div>
                <div className="h-4 w-full rounded bg-slate-800/60"></div>
                <div className="h-4 w-1/2 rounded bg-slate-800/60"></div>
              </div>
            ))}
          </div>
        ) : displayItems.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/30 p-12 text-center">
            <FileText className="mx-auto h-12 w-12 text-slate-600" />
            <h3 className="mt-4 text-base font-semibold text-white">
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
              return (
                <div
                  key={doc.id}
                  className="group relative rounded-xl border border-slate-800 bg-slate-900/60 p-6 backdrop-blur-sm transition-all hover:border-slate-700 hover:shadow-xl hover:shadow-slate-900/50 flex flex-col justify-between"
                >
                  <div className="space-y-3 cursor-pointer" onClick={() => onSelectDoc(doc.id)}>
                    <div className="flex items-center justify-between gap-2">
                      <span
                        className={`inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold ${getClassificationBadge(
                          doc.classification
                        )}`}
                      >
                        {doc.classification}
                      </span>
                      <span
                        className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${getStatusBadge(
                          doc.status
                        )}`}
                      >
                        {doc.status}
                      </span>
                    </div>

                    <h3 className="text-lg font-semibold text-white group-hover:text-teal-400 transition-colors line-clamp-1">
                      {doc.title}
                    </h3>

                    {hasSnippet ? (
                      <div
                        className="text-xs text-slate-300 bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80 font-serif leading-relaxed"
                        dangerouslySetInnerHTML={{ __html: doc.snippet }}
                      />
                    ) : (
                      <p className="text-xs text-slate-400 line-clamp-2">
                        {doc.description || 'No document description provided.'}
                      </p>
                    )}
                  </div>

                  <div className="mt-6 pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-500">
                    <span className="flex items-center gap-1 font-mono text-slate-400">
                      v{doc.currentVersionNumber || 1}
                    </span>

                    <div className="flex items-center gap-2">
                      {isDraft && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setReviewModalDoc({ id: doc.id, title: doc.title });
                          }}
                          className="inline-flex items-center gap-1 text-[11px] font-semibold text-teal-400 hover:text-teal-300 bg-teal-500/10 px-2 py-1 rounded-md border border-teal-500/20 transition-colors"
                        >
                          <Send className="h-3 w-3" />
                          <span>Submit Review</span>
                        </button>
                      )}
                      <span className="flex items-center gap-1">
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
