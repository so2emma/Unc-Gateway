package com.unc.gateway.plugins.property;

import com.unc.gateway.plugins.RateLimitResult;
import com.unc.gateway.plugins.RedisSlidingWindowRateLimiter;
import net.jqwik.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

class RateLimiterPropertyTest {

    static class TestClock extends Clock {
        private final AtomicLong millis = new AtomicLong(1_000_000L);

        public void setMillis(long m) {
            millis.set(m);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Property(tries = 150)
    @Label("QUOTA_BOUNDARY: number of allowed requests within a single sliding window never exceeds limit N")
    boolean quotaBoundaryNeverExceeded(
            @ForAll("limits") long limit,
            @ForAll("windowSeconds") long windowSeconds,
            @ForAll("timestampOffsets") List<Long> offsets) {

        TestClock testClock = new TestClock();
        long baseTime = 1_000_000L;
        testClock.setMillis(baseTime);

        RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter(testClock);
        String consumerKey = "test-consumer-" + UUID.randomUUID();

        long windowMs = windowSeconds * 1000L;
        // Map all generated offsets into the single sliding window [0, windowMs - 1]
        List<Long> normalizedOffsets = offsets.stream()
                .map(off -> Math.abs(off) % Math.max(1, windowMs))
                .sorted()
                .toList();

        int allowedCount = 0;
        int rejectedCount = 0;

        for (Long offset : normalizedOffsets) {
            testClock.setMillis(baseTime + offset);
            RateLimitResult result = limiter.tryAcquire(consumerKey, limit, windowSeconds).block();
            if (result != null && result.allowed()) {
                allowedCount++;
            } else {
                rejectedCount++;
            }
        }

        // Must never exceed configured limit N, and within single window allows exactly min(total, limit)
        return allowedCount <= limit && allowedCount == Math.min(normalizedOffsets.size(), (int) limit);
    }

    @Property(tries = 150)
    @Label("CONCURRENCY: concurrent requests across threads never exceed configured limit N")
    boolean concurrencyNeverExceedsLimit(
            @ForAll("limits") long limit,
            @ForAll("batchSizes") int extraRequests) {

        int batchSize = (int) limit + extraRequests;
        RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter();
        String consumerKey = "concurrent-consumer-" + UUID.randomUUID();
        long windowSeconds = 10;

        List<CompletableFuture<RateLimitResult>> futures = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    limiter.tryAcquire(consumerKey, limit, windowSeconds).block()
            ));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int allowedCount = 0;
        int rejectedCount = 0;

        for (CompletableFuture<RateLimitResult> future : futures) {
            RateLimitResult result = future.join();
            if (result != null && result.allowed()) {
                allowedCount++;
            } else {
                rejectedCount++;
            }
        }

        // Concurrency cap: allowed requests must never exceed limit N
        return allowedCount <= limit && allowedCount == limit && rejectedCount == extraRequests;
    }

    @Provide
    Arbitrary<Long> limits() {
        return Arbitraries.longs().between(1, 40);
    }

    @Provide
    Arbitrary<Long> windowSeconds() {
        return Arbitraries.longs().between(1, 30);
    }

    @Provide
    Arbitrary<List<Long>> timestampOffsets() {
        return Arbitraries.longs().between(0, 60_000)
                .list().ofMinSize(1).ofMaxSize(100);
    }

    @Provide
    Arbitrary<Integer> batchSizes() {
        return Arbitraries.integers().between(1, 30);
    }
}
