package com.knowflow.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "knowflow.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private final Path baseDirectory;

    public LocalFileStorage(@Value("${knowflow.storage.local.base-dir:./storage}") String baseDir) {
        this.baseDirectory = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file storage root: " + this.baseDirectory, e);
        }
    }

    @Override
    public String store(InputStream content, String extension) {
        String cleanExt = (extension != null && extension.startsWith(".")) ? extension.substring(1) : (extension != null ? extension : "bin");
        String dateSubdir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uniqueFileName = UUID.randomUUID().toString() + "." + cleanExt;
        
        Path targetDir = baseDirectory.resolve(dateSubdir).normalize();
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(uniqueFileName).normalize();

            // Guard against path traversal
            if (!targetFile.startsWith(baseDirectory)) {
                throw new SecurityException("Storage path traversal attempted: " + targetFile);
            }

            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return dateSubdir + "/" + uniqueFileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file payload", e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path targetFile = resolveAndValidate(storageKey);
        try {
            return Files.newInputStream(targetFile);
        } catch (IOException e) {
            throw new IllegalArgumentException("Stored file not found for key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.trim().isEmpty()) return;
        try {
            Path targetFile = resolveAndValidate(storageKey);
            Files.deleteIfExists(targetFile);
        } catch (Exception e) {
            log.warn("Failed to delete stored file: {}", storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.trim().isEmpty()) return false;
        try {
            Path targetFile = resolveAndValidate(storageKey);
            return Files.exists(targetFile);
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveAndValidate(String storageKey) {
        Path target = baseDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new SecurityException("Illegal access outside storage boundary");
        }
        return target;
    }
}
