package com.knowflow.documents;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class FileValidationService {

    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    /**
     * Inspects magic bytes to prevent renamed executables or malicious files.
     */
    public void validatePdfMagicBytes(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length < 4) {
            throw new IllegalArgumentException("File content is too small or empty");
        }

        for (int i = 0; i < 4; i++) {
            if (headerBytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new IllegalArgumentException("Uploaded file signature does not match PDF format");
            }
        }
    }

    /**
     * Computes the SHA-256 checksum digest of an input byte array.
     */
    public String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
