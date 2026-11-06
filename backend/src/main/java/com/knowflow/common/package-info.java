package com.knowflow.common;

/**
 * Common module: Cross-cutting concerns including standardized API error envelopes,
 * correlation ID propagation, global exception handling, and time abstractions.
 *
 * <p>Responsibility:
 * <ul>
 *   <li>Provide uniform {@code ApiError} representation across all controllers.</li>
 *   <li>Inject and pass {@code X-Correlation-Id} via MDC and HTTP response headers.</li>
 *   <li>Provide mockable {@code Clock} bean for deterministic time testing.</li>
 * </ul>
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not depend on feature-specific business modules (identity, documents, etc.).</li>
 *   <li>Must not contain business logic or domain entities.</li>
 * </ul>
 */
