package com.knowflow.search;

/**
 * Search module: Hybrid keyword (Postgres tsvector) and semantic (pgvector) retrieval.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not return content before permission filters are applied in SQL.</li>
 *   <li>Must not expose unfiltered search queries that leak restricted document existence.</li>
 * </ul>
 */
