package com.knowflow.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FileStorageTest {

    @Test
    @DisplayName("S3FileStorage validates inputs and constructs endpoints properly")
    void testS3StorageInitialization() {
        S3FileStorage storage = new S3FileStorage(
                "http://localhost:9000",
                "knowflow-test-bucket",
                "test-access-key",
                "test-secret-key",
                "us-east-1",
                () -> null
        );

        assertThat(storage).isNotNull();

        // Null or blank keys in retrieve throw IllegalArgumentException
        assertThatThrownBy(() -> storage.retrieve(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> storage.retrieve("   "))
                .isInstanceOf(IllegalArgumentException.class);

        // Delete with null/blank is a safe no-op
        storage.delete(null);
        storage.delete("");

        // Exists with null/blank returns false
        assertThat(storage.exists(null)).isFalse();
        assertThat(storage.exists("")).isFalse();
    }
}
