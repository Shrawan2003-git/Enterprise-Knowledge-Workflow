package com.knowflow.documents;

/**
 * Documents module: Governed document metadata, versioning, explicit ACL policies, and storage ports.
 *
 * <p>Must NOT do:
 * <ul>
 *   <li>Must not store large binary files directly in relational rows by default.</li>
 *   <li>Must not derive storage paths from raw user-supplied filenames.</li>
 * </ul>
 */
