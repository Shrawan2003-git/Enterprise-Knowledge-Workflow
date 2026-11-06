package com.knowflow.copilot;

import com.knowflow.security.CurrentUser;

import java.util.List;

public interface RetrievalService {

    /**
     * Retrieve authorized passages relevant to the question using RRF hybrid search.
     * Enforces ACL at the SQL level, active approved versions only, and defense-in-depth policy verification.
     */
    List<RetrievedPassage> retrieve(String question, CurrentUser user, RetrievalOptions options);
}
