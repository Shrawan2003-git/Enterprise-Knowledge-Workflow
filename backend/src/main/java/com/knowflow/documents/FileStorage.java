package com.knowflow.documents;

import java.io.InputStream;

public interface FileStorage {
    /**
     * Store file bytes and return a generated, secure storage key.
     * The storage key must NEVER be derived from user-supplied filenames.
     */
    String store(InputStream content, String extension);

    /**
     * Retrieve the stream of file bytes for a valid storage key.
     */
    InputStream retrieve(String storageKey);

    /**
     * Delete stored file bytes (for transactional cleanups or retention purges).
     */
    void delete(String storageKey);

    /**
     * Verify whether a file exists for the given storage key.
     */
    boolean exists(String storageKey);
}
