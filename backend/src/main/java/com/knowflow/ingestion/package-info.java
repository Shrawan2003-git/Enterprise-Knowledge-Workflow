package com.knowflow.ingestion;

/**
 * Ingestion module: Async background pipelines for extraction, normalization, chunking, and indexing.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not run long extraction/embedding operations inside user-facing HTTP requests.</li>
 *   <li>Must not produce duplicate chunks or embeddings on job retries.</li>
 * </ul>
 */
