package com.knowflow.approvals;

/**
 * Approvals module: Explicit document version review state machine and immutable history.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not allow arbitrary status updates from generic CRUD endpoints.</li>
 *   <li>Must not permit submitters to approve their own requests.</li>
 * </ul>
 */
