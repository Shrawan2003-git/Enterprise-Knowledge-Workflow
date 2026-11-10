import React, { useState, useEffect, useRef } from 'react';
import {
  conversationApi,
  ConversationSummary,
  ConversationDetail,
  MessageItem,
  CitationItem,
  FeedbackReasonCode
} from '../api/conversationApi';
import {
  Sparkles,
  Send,
  Plus,
  Trash2,
  ThumbsUp,
  ThumbsDown,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  BookOpen,
  RefreshCw,
  ExternalLink,
  ShieldAlert,
  MessageSquare,
  X
} from 'lucide-react';

interface CopilotViewProps {
  onNavigateToDocument: (documentId: string, versionNumber?: number, pageHint?: string) => void;
  onNavigateToApprovals?: () => void;
}

const SAMPLE_QUESTIONS = [
  'What is the company annual vacation policy?',
  'How do employees submit expense claims?',
  'What is the password rotation interval?',
  'What are the standard working hours for remote workers?',
  'What is the procedure to report a security incident?'
];

export const CopilotView: React.FC<CopilotViewProps> = ({
  onNavigateToDocument
}) => {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [currentConversation, setCurrentConversation] = useState<ConversationDetail | null>(null);
  const [activeConvoId, setActiveConvoId] = useState<string | null>(null);
  const [, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [inputContent, setInputContent] = useState('');
  const [notFound, setNotFound] = useState(false);
  const [, setError] = useState<string | null>(null);
  const [sourceAlert, setSourceAlert] = useState<string | null>(null);

  // Feedback Modal State
  const [feedbackModalMsgId, setFeedbackModalMsgId] = useState<string | null>(null);
  const [feedbackRating, setFeedbackRating] = useState<number>(1);
  const [feedbackReason, setFeedbackReason] = useState<FeedbackReasonCode>('INCORRECT');
  const [feedbackComment, setFeedbackComment] = useState('');

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadConversations();
  }, []);

  useEffect(() => {
    if (activeConvoId) {
      loadConversationDetail(activeConvoId);
    }
  }, [activeConvoId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [currentConversation?.messages, sending]);

  const loadConversations = async () => {
    try {
      const list = await conversationApi.listConversations();
      setConversations(list);
      if (!activeConvoId && list.length > 0) {
        setActiveConvoId(list[0].id);
      }
    } catch (err: any) {
      console.error('Failed to load conversations:', err);
    }
  };

  const loadConversationDetail = async (id: string) => {
    setLoading(true);
    setNotFound(false);
    setError(null);
    try {
      const detail = await conversationApi.getConversation(id);
      setCurrentConversation(detail);
    } catch (err: any) {
      if (err.message === 'NOT_FOUND') {
        setNotFound(true);
      } else {
        setError(err.message || 'Failed to load conversation thread.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleStartNewChat = async () => {
    setLoading(true);
    setError(null);
    setNotFound(false);
    try {
      const created = await conversationApi.createConversation();
      setConversations((prev) => [
        {
          id: created.id,
          title: created.title,
          createdAt: created.createdAt,
          updatedAt: created.updatedAt,
          messageCount: 0
        },
        ...prev
      ]);
      setActiveConvoId(created.id);
      setCurrentConversation(created);
    } catch (err: any) {
      setError('Failed to create new conversation');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteConversation = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    try {
      await conversationApi.deleteConversation(id);
      const remaining = conversations.filter((c) => c.id !== id);
      setConversations(remaining);
      if (activeConvoId === id) {
        if (remaining.length > 0) {
          setActiveConvoId(remaining[0].id);
        } else {
          setActiveConvoId(null);
          setCurrentConversation(null);
        }
      }
    } catch (err: any) {
      setError('Failed to delete conversation');
    }
  };

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputContent).trim();
    if (!text || sending) return;

    let convoId = activeConvoId;
    if (!convoId) {
      try {
        const created = await conversationApi.createConversation();
        convoId = created.id;
        setActiveConvoId(convoId);
        setCurrentConversation(created);
        setConversations((prev) => [
          {
            id: created.id,
            title: created.title,
            createdAt: created.createdAt,
            updatedAt: created.updatedAt,
            messageCount: 0
          },
          ...prev
        ]);
      } catch (err: any) {
        setError('Failed to start conversation');
        return;
      }
    }

    setSending(true);
    setError(null);
    setSourceAlert(null);
    setInputContent('');

    // Optimistic user message
    const tempUserMsg: MessageItem = {
      id: 'temp-' + Date.now(),
      conversationId: convoId,
      role: 'user',
      content: text,
      model: null,
      promptVersion: null,
      confidence: null,
      createdAt: new Date().toISOString(),
      citations: [],
      feedback: null
    };

    setCurrentConversation((prev) =>
      prev ? { ...prev, messages: [...prev.messages, tempUserMsg] } : null
    );

    try {
      const assistantMsg = await conversationApi.postMessage(convoId, text);
      setCurrentConversation((prev) => {
        if (!prev) return null;
        const filtered = prev.messages.filter((m) => m.id !== tempUserMsg.id);
        return {
          ...prev,
          messages: [...filtered, tempUserMsg, assistantMsg]
        };
      });
      loadConversations();
    } catch (err: any) {
      setError(err.message || 'Failed to generate response.');
    } finally {
      setSending(false);
    }
  };

  const handleCitationClick = async (citation: CitationItem) => {
    setSourceAlert(null);
    try {
      await conversationApi.verifyCitation(citation.id);
      onNavigateToDocument(citation.documentId, citation.versionNumber, citation.pageHint);
    } catch (err: any) {
      setSourceAlert(
        `Source document "${citation.documentTitle}" (v${citation.versionNumber}) is no longer available or your access has been revoked.`
      );
    }
  };

  const handleQuickFeedback = async (msgId: string, rating: number) => {
    if (rating === -1) {
      setFeedbackModalMsgId(msgId);
      setFeedbackRating(-1);
      return;
    }
    try {
      const fb = await conversationApi.submitFeedback(msgId, 1);
      updateMessageFeedback(msgId, fb);
    } catch (err: any) {
      console.error('Failed to submit thumbs up:', err);
    }
  };

  const handleSubmitModalFeedback = async () => {
    if (!feedbackModalMsgId) return;
    try {
      const fb = await conversationApi.submitFeedback(
        feedbackModalMsgId,
        feedbackRating,
        feedbackReason,
        feedbackComment
      );
      updateMessageFeedback(feedbackModalMsgId, fb);
      setFeedbackModalMsgId(null);
      setFeedbackComment('');
    } catch (err: any) {
      console.error('Failed to submit detailed feedback:', err);
    }
  };

  const updateMessageFeedback = (msgId: string, feedback: any) => {
    setCurrentConversation((prev) => {
      if (!prev) return null;
      return {
        ...prev,
        messages: prev.messages.map((m) => (m.id === msgId ? { ...m, feedback } : m))
      };
    });
  };

  const renderConfidenceBadge = (confidence: MessageItem['confidence']) => {
    switch (confidence) {
      case 'ANSWERED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
            <CheckCircle2 className="h-3 w-3" />
            Verified Grounded
          </span>
        );
      case 'CONFLICT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-500/10 text-amber-400 border border-amber-500/30">
            <AlertTriangle className="h-3 w-3" />
            Conflicting Sources
          </span>
        );
      case 'INSUFFICIENT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-slate-500/10 text-slate-400 border border-slate-500/30">
            <AlertCircle className="h-3 w-3" />
            Insufficient Evidence
          </span>
        );
      default:
        return null;
    }
  };

  return (
    <div className="flex-1 flex flex-col lg:flex-row h-[calc(100vh-140px)] overflow-hidden max-w-7xl mx-auto w-full gap-4">
      {/* Sidebar: Conversation List (Clay) */}
      <aside className="w-full lg:w-72 flex flex-col clay-card p-4 overflow-hidden shrink-0 shadow-xl border border-slate-700/20">
        <div className="pb-3 border-b border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <MessageSquare className="h-4 w-4 text-teal-400" />
            <span className="text-xs font-bold text-white uppercase tracking-wider">Conversations</span>
          </div>
          <button
            onClick={handleStartNewChat}
            className="clay-btn-teal flex items-center gap-1 text-xs text-white font-bold px-3 py-1.5 shadow-md"
          >
            <Plus className="h-3.5 w-3.5" />
            New Chat
          </button>
        </div>

        <div className="flex-1 overflow-y-auto py-2 flex flex-col gap-1.5">
          {conversations.length === 0 ? (
            <div className="text-xs text-slate-500 italic p-4 text-center">
              No conversations yet. Start a new chat!
            </div>
          ) : (
            conversations.map((c) => {
              const isSelected = c.id === activeConvoId;
              return (
                <div
                  key={c.id}
                  onClick={() => setActiveConvoId(c.id)}
                  className={`group flex items-center justify-between p-3 rounded-2xl text-left cursor-pointer transition-all ${
                    isSelected
                      ? 'clay-card-elevated border-l-4 border-teal-400 text-teal-300'
                      : 'clay-btn-secondary text-slate-300 hover:text-white'
                  }`}
                >
                  <div className="flex flex-col min-w-0 pr-2">
                    <span className="text-xs font-bold truncate">{c.title}</span>
                    <span className="text-[10px] text-slate-400 mt-0.5">
                      {c.messageCount} msg · {new Date(c.updatedAt).toLocaleDateString()}
                    </span>
                  </div>
                  <button
                    onClick={(e) => handleDeleteConversation(e, c.id)}
                    className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-rose-400 p-1 rounded-lg transition-opacity"
                    title="Delete conversation"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              );
            })
          )}
        </div>

        <div className="pt-3 border-t border-slate-800/80 text-[10px] text-slate-400 flex items-center gap-1.5 font-medium">
          <ShieldAlert className="h-3.5 w-3.5 text-teal-400 shrink-0" />
          <span>Zero-Trust ACL & Server-Audited</span>
        </div>
      </aside>

      {/* Main Chat Thread Area (Clay) */}
      <section className="flex-1 flex flex-col clay-card overflow-hidden shadow-2xl relative border border-slate-700/20">
        {/* Thread Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-xl clay-btn-teal text-white flex items-center justify-center shadow-md">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-white truncate max-w-md">
                {currentConversation?.title || 'Enterprise KnowFlow Copilot'}
              </h2>
              <p className="text-[11px] text-slate-400 font-medium">
                Grounded in enterprise docs with page-aware citation verification
              </p>
            </div>
          </div>

          <div className="text-[11px] text-slate-400 flex items-center gap-2">
            <span className="clay-badge-cyan px-2.5 py-0.5 text-[10px] font-bold">1536d pgvector</span>
          </div>
        </div>

        {/* Source Revocation Alert */}
        {sourceAlert && (
          <div className="mx-4 mt-3 p-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-amber-400 shrink-0" />
              <span>{sourceAlert}</span>
            </div>
            <button
              onClick={() => setSourceAlert(null)}
              className="text-slate-400 hover:text-white text-xs underline ml-2"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Messages Stream Container */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 flex flex-col gap-6" aria-live="polite">
          {notFound ? (
            <div className="flex flex-col items-center justify-center h-full text-center p-6">
              <AlertCircle className="h-10 w-10 text-rose-400 mb-3" />
              <h3 className="text-base font-semibold text-white">Conversation Not Found</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-sm">
                This conversation does not exist or you do not have permission to view it.
              </p>
              <button
                onClick={handleStartNewChat}
                className="mt-4 px-4 py-2 rounded-xl bg-teal-500 text-slate-950 font-semibold text-xs hover:bg-teal-400 transition-colors"
              >
                Start New Chat
              </button>
            </div>
          ) : !currentConversation || currentConversation.messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center p-6 gap-4">
              <div className="h-12 w-12 rounded-2xl bg-teal-500/10 border border-teal-500/30 text-teal-400 flex items-center justify-center">
                <Sparkles className="h-6 w-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Ask KnowFlow Copilot</h3>
                <p className="text-xs text-slate-400 mt-1 max-w-md">
                  Ask policy questions, check approval tasks, or query IT guidelines grounded in authorized docs.
                </p>
              </div>

              <div className="flex flex-wrap justify-center gap-2 max-w-lg mt-2">
                {SAMPLE_QUESTIONS.map((q, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleSendMessage(q)}
                    className="text-xs bg-slate-800/80 hover:bg-slate-700 text-slate-300 hover:text-white px-3 py-1.5 rounded-xl border border-slate-700/60 transition-colors"
                  >
                    {q}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            currentConversation.messages.map((m) => {
              const isUser = m.role === 'user';
              return (
                <div
                  key={m.id}
                  className={`flex flex-col gap-2 max-w-3xl ${isUser ? 'self-end items-end' : 'self-start items-start'}`}
                >
                  <div className="flex items-center gap-2 px-1 text-[11px] text-slate-500">
                    <span>{isUser ? 'You' : 'Copilot'}</span>
                    <span>·</span>
                    <span>{new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    {!isUser && renderConfidenceBadge(m.confidence)}
                  </div>

                  {/* Message Bubble (Cosmic Glass) */}
                  <div
                    className={`text-sm leading-relaxed ${
                      isUser
                        ? 'btn-electric py-3.5 px-5 text-white max-w-[85%] rounded-3xl rounded-br-none shadow-lg font-medium'
                        : 'neon-radiant-card p-5 text-slate-200 max-w-[95%] space-y-3 rounded-3xl rounded-bl-none shadow-xl'
                    }`}
                  >
                    <div className="whitespace-pre-wrap">{m.content}</div>

                    {/* Citations List on Assistant message */}
                    {!isUser && m.citations && m.citations.length > 0 && (
                      <div className="mt-4 pt-3 border-t border-blue-400/20 flex flex-col gap-2">
                        <span className="text-[11px] font-bold text-blue-300 flex items-center gap-1.5 uppercase tracking-wider">
                          <BookOpen className="h-3.5 w-3.5 text-blue-400" />
                          Verified Grounding Sources:
                        </span>
                        <div className="flex flex-wrap gap-2">
                          {m.citations.map((c) => (
                            <button
                              key={c.id}
                              onClick={() => handleCitationClick(c)}
                              className="pill-badge-active group flex items-center gap-1.5 px-3 py-1 font-bold text-xs transition-transform hover:scale-105"
                            >
                              <span className="font-mono text-blue-300 text-[11px]">
                                {c.sourceId}
                              </span>
                              <span className="truncate max-w-[180px]">
                                {c.documentTitle}
                              </span>
                              <span className="text-blue-200 text-[10px]">
                                (v{c.versionNumber}, {c.pageHint})
                              </span>
                              <ExternalLink className="h-3 w-3 text-blue-300" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Assistant Feedback Controls */}
                  {!isUser && (
                    <div className="flex items-center gap-2 px-1 text-slate-500">
                      <button
                        onClick={() => handleQuickFeedback(m.id, 1)}
                        className={`clay-btn-secondary p-1.5 rounded-lg hover:text-emerald-400 transition-colors ${
                          m.feedback?.rating === 1 ? 'text-emerald-400' : 'text-slate-400'
                        }`}
                        title="Helpful & accurate"
                      >
                        <ThumbsUp className="h-3.5 w-3.5" />
                      </button>
                      <button
                        onClick={() => handleQuickFeedback(m.id, -1)}
                        className={`clay-btn-secondary p-1.5 rounded-lg hover:text-rose-400 transition-colors ${
                          m.feedback?.rating === -1 ? 'text-rose-400' : 'text-slate-400'
                        }`}
                        title="Report issue with answer"
                      >
                        <ThumbsDown className="h-3.5 w-3.5" />
                      </button>
                      {m.feedback && (
                        <span className="text-[10px] text-slate-500 italic ml-1">
                          Feedback recorded
                        </span>
                      )}
                    </div>
                  )}
                </div>
              );
            })
          )}

          {sending && (
            <div className="self-start flex items-center gap-2 p-4 rounded-2xl clay-card-elevated border border-slate-700/30 text-xs text-slate-300">
              <RefreshCw className="h-3.5 w-3.5 animate-spin text-teal-400" />
              <span>Retrieving authorized evidence and reasoning...</span>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar (Clay) */}
        <div className="p-4 border-t border-slate-800/80 bg-slate-950/60">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSendMessage();
            }}
            className="flex items-center gap-3"
          >
            <input
              ref={inputRef}
              type="text"
              value={inputContent}
              onChange={(e) => setInputContent(e.target.value)}
              placeholder="Type your question (grounded in authorized documents)..."
              disabled={sending}
              className="clay-input flex-1 py-3.5 px-4 text-sm text-white placeholder-slate-500 focus:outline-none disabled:opacity-50"
            />
            <button
              type="submit"
              disabled={sending || !inputContent.trim()}
              className="clay-btn-teal px-5 py-3 text-sm font-bold text-white shadow-md disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5 shrink-0"
            >
              <Send className="h-4 w-4" />
              <span>Send</span>
            </button>
          </form>
        </div>
      </section>

      {/* Feedback Dialog Modal */}
      {feedbackModalMsgId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <ThumbsDown className="h-4 w-4 text-rose-400" />
                Submit Answer Feedback
              </h3>
              <button
                onClick={() => setFeedbackModalMsgId(null)}
                className="text-slate-500 hover:text-white"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-xs text-slate-400 font-medium">Issue category</label>
              <select
                value={feedbackReason}
                onChange={(e) => setFeedbackReason(e.target.value as FeedbackReasonCode)}
                className="bg-slate-950 border border-slate-800 rounded-xl p-2.5 text-xs text-slate-200 focus:border-teal-500 focus:outline-none"
              >
                <option value="INCORRECT">Incorrect facts or hallucination</option>
                <option value="INCOMPLETE">Incomplete or missing required detail</option>
                <option value="UNSUPPORTED_CITATION">Unsupported or fabricated citation</option>
                <option value="OUTDATED_SOURCE">Outdated policy / old version</option>
                <option value="PERMISSION_ISSUE">Permission boundary issue</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-xs text-slate-400 font-medium">Optional comment</label>
              <textarea
                value={feedbackComment}
                onChange={(e) => setFeedbackComment(e.target.value)}
                placeholder="Describe what was missing or incorrect..."
                rows={3}
                className="bg-slate-950 border border-slate-800 rounded-xl p-3 text-xs text-slate-200 placeholder-slate-500 focus:border-teal-500 focus:outline-none resize-none"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setFeedbackModalMsgId(null)}
                className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white border border-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={handleSubmitModalFeedback}
                className="px-4 py-1.5 rounded-lg text-xs font-semibold bg-rose-500 text-white hover:bg-rose-400 transition-colors"
              >
                Submit Feedback
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
