package com.knowflow.tools;

/**
 * Tools module: Allowlisted, typed business operations consumable by AI Copilot with strict validation.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not expose arbitrary SQL, shell execution, or unrestricted HTTP access.</li>
 *   <li>Must not trust caller-supplied user IDs from tool argument models.</li>
 * </ul>
 */
