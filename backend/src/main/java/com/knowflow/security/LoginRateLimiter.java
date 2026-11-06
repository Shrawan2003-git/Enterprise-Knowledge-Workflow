package com.knowflow.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket rate limiter for authentication endpoints.
 * Limits login attempts per IP/Email pair to 5 requests per 15 minutes.
 */
@Component
public class LoginRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createBucket() {
        // 5 tokens per 15 minutes
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());
        return bucket.tryConsume(1);
    }

    public void reset(String key) {
        buckets.remove(key);
    }
}
