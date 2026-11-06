package com.knowflow.security;

/**
 * Platform security configuration, JWT authentication filters, and CurrentUser resolver.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not trust caller-supplied user IDs from request bodies or parameters.</li>
 *   <li>Must not expose sensitive tokens, keys, or passwords.</li>
 * </ul>
 */
