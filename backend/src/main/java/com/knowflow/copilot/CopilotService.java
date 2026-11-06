package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;

public interface CopilotService {

    /**
     * Executes grounded RAG generation for the given user question.
     * Enforces strict authorization filtering, rate limiting, untrusted data separation,
     * citation validation, and abstention on insufficient/conflicting evidence.
     */
    CopilotAnswer ask(String question, CurrentUser user, RetrievalOptions options);
}
