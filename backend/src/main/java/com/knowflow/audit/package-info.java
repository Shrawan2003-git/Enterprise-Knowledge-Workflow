package com.knowflow.audit;

/**
 * Audit module: Append-only event tracking for security, compliance, and governance actions.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not expose UPDATE or DELETE capabilities on audit log repositories.</li>
 *   <li>Must not log raw credentials, tokens, or unredacted confidential file payloads.</li>
 * </ul>
 */
