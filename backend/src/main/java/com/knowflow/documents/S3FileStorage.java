package com.knowflow.documents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Production-ready S3 / MinIO compatible implementation of FileStorage.
 * Communicates over standard AWS S3 REST API with SigV4 authentication
 * using the built-in Java 21 HttpClient (zero extra external dependencies).
 */
@Component
@ConditionalOnProperty(name = "knowflow.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final java.util.function.Supplier<HttpClient> httpClientSupplier;

    public S3FileStorage(
            @Value("${knowflow.storage.s3.endpoint:http://localhost:9000}") String endpoint,
            @Value("${knowflow.storage.s3.bucket:knowflow-docs}") String bucket,
            @Value("${knowflow.storage.s3.access-key:minioadmin}") String accessKey,
            @Value("${knowflow.storage.s3.secret-key:minioadmin}") String secretKey,
            @Value("${knowflow.storage.s3.region:us-east-1}") String region) {
        this(endpoint, bucket, accessKey, secretKey, region, () -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public S3FileStorage(
            String endpoint,
            String bucket,
            String accessKey,
            String secretKey,
            String region,
            java.util.function.Supplier<HttpClient> httpClientSupplier) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.httpClientSupplier = httpClientSupplier;
    }

    @Override
    public String store(InputStream content, String extension) {
        String cleanExt = (extension != null && extension.startsWith(".")) ? extension.substring(1) : (extension != null ? extension : "bin");
        String dateSubdir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String key = dateSubdir + "/" + UUID.randomUUID() + "." + cleanExt;

        try {
            byte[] bytes = content.readAllBytes();
            URI uri = buildObjectUri(key);
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .header("Content-Type", resolveContentType(cleanExt))
                    .header("Content-Length", String.valueOf(bytes.length));

            signRequest(reqBuilder, "PUT", uri.getPath(), "", bytes, now);

            HttpResponse<Void> response = httpClientSupplier.get().send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("S3 store failed with HTTP " + response.statusCode());
            }

            return key;
        } catch (Exception e) {
            log.error("Failed to store file in S3 for bucket {}", bucket, e);
            throw new RuntimeException("S3 storage upload error: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Invalid empty storage key");
        }

        try {
            URI uri = buildObjectUri(storageKey);
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                    .GET();

            signRequest(reqBuilder, "GET", uri.getPath(), "", new byte[0], now);

            HttpResponse<InputStream> response = httpClientSupplier.get().send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                throw new IllegalArgumentException("Stored file not found for key: " + storageKey);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("S3 retrieve failed with HTTP " + response.statusCode());
            }

            return response.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve file from S3: {}", storageKey, e);
            throw new RuntimeException("S3 storage retrieve error: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;

        try {
            URI uri = buildObjectUri(storageKey);
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                    .DELETE();

            signRequest(reqBuilder, "DELETE", uri.getPath(), "", new byte[0], now);

            HttpResponse<Void> response = httpClientSupplier.get().send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400 && response.statusCode() != 404) {
                log.warn("S3 delete received status {} for key {}", response.statusCode(), storageKey);
            }
        } catch (Exception e) {
            log.warn("Failed to delete file from S3: {}", storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return false;

        try {
            URI uri = buildObjectUri(storageKey);
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody());

            signRequest(reqBuilder, "HEAD", uri.getPath(), "", new byte[0], now);

            HttpResponse<Void> response = httpClientSupplier.get().send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private URI buildObjectUri(String key) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        return URI.create(endpoint + "/" + bucket + "/" + cleanKey);
    }

    private String resolveContentType(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private void signRequest(HttpRequest.Builder reqBuilder, String method, String canonicalUri, String queryString, byte[] payload, ZonedDateTime time) {
        if (accessKey == null || accessKey.isBlank()) {
            return; // Anonymous / local unauthenticated dev mode
        }

        try {
            String amzDate = time.format(ISO_DATE_FORMAT);
            String dateStamp = time.format(DATE_ONLY_FORMAT);
            String host = URI.create(endpoint).getAuthority();

            reqBuilder.header("x-amz-date", amzDate);
            reqBuilder.header("host", host);

            String payloadHash = HexFormat.of().formatHex(sha256(payload));
            reqBuilder.header("x-amz-content-sha256", payloadHash);

            String canonicalHeaders = "host:" + host + "\n" +
                    "x-amz-content-sha256:" + payloadHash + "\n" +
                    "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

            String canonicalRequest = method + "\n" +
                    canonicalUri + "\n" +
                    queryString + "\n" +
                    canonicalHeaders + "\n" +
                    signedHeaders + "\n" +
                    payloadHash;

            String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" +
                    amzDate + "\n" +
                    credentialScope + "\n" +
                    HexFormat.of().formatHex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

            byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, "s3");
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

            String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope +
                    ", SignedHeaders=" + signedHeaders +
                    ", Signature=" + signature;

            reqBuilder.header("Authorization", authHeader);
        } catch (Exception e) {
            log.error("Failed to generate SigV4 signature for S3 request", e);
            throw new RuntimeException("SigV4 error: " + e.getMessage(), e);
        }
    }

    private byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }
}
