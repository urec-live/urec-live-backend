package com.ureclive.urec_live_backend.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, this::newBucket);
    }

    private Bucket newBucket(String key) {
        // Allow 10 tokens per minute
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
