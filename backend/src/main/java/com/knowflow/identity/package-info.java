package com.knowflow.identity;

/**
 * Identity module: Manages user accounts, credentials, activation status, and role mappings.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not depend on document internals to make authorization decisions implicitly.</li>
 *   <li>Must not log raw credentials, password hashes, or sensitive tokens.</li>
 * </ul>
 */
