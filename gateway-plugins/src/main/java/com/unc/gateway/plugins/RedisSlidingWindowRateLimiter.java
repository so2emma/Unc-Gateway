package com.unc.gateway.plugins;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window rate limiter using Redis ZSET with an atomic Lua script.
 * Supports an injectable {@link Clock} and an in-memory simulation for local/testing execution.
 */
@Component
public class RedisSlidingWindowRateLimiter {

    static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]

            local clearBefore = now - windowMs
            redis.call('ZADD', key, now, member)
            redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)
            local count = redis.call('ZCARD', key)

            if count <= limit then
                redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 2)
                return {1, 0, limit - count}
            else
                redis.call('ZREM', key, member)
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retryAfter = 1
                if #oldest >= 2 then
                    local oldestScore = tonumber(oldest[2])
                    local remainingMs = (oldestScore + windowMs) - now
                    retryAfter = math.ceil(remainingMs / 1000)
                    if retryAfter < 1 then
                        retryAfter = 1
                    end
                else
                    retryAfter = math.ceil(windowMs / 1000)
                end
                return {0, retryAfter, 0}
            end
            """;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> redisScript = RedisScript.of(SLIDING_WINDOW_LUA, List.class);
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final AtomicLong memberSequence = new AtomicLong(0);

    // In-memory sliding-window state used when redisTemplate is absent (e.g. unit tests with fake Clock)
    private final Map<String, NavigableMap<Long, List<String>>> inMemoryStore = new ConcurrentHashMap<>();

    public RedisSlidingWindowRateLimiter() {
        this(null, Clock.systemUTC());
    }

    public RedisSlidingWindowRateLimiter(Clock clock) {
        this(null, clock);
    }

    public RedisSlidingWindowRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    public RedisSlidingWindowRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Attempts to acquire quota for the given key.
     *
     * @param consumerKey   identifying key for the consumer quota bucket
     * @param limit         maximum requests allowed in the sliding window
     * @param windowSeconds duration of the sliding window in seconds
     * @return {@link Mono} emitting {@link RateLimitResult}
     */
    public Mono<RateLimitResult> tryAcquire(String consumerKey, long limit, long windowSeconds) {
        return tryAcquire(consumerKey, limit, Duration.ofSeconds(windowSeconds));
    }

    /**
     * Attempts to acquire quota for the given key.
     *
     * @param consumerKey identifying key for the consumer quota bucket
     * @param limit       maximum requests allowed in the sliding window
     * @param window      duration of the sliding window
     * @return {@link Mono} emitting {@link RateLimitResult}
     */
    public Mono<RateLimitResult> tryAcquire(String consumerKey, long limit, Duration window) {
        if (consumerKey == null || consumerKey.isBlank()) {
            return Mono.just(RateLimitResult.allowed(limit));
        }

        long now = clock.millis();
        long windowMs = Math.max(1, window.toMillis());
        String member = now + ":" + memberSequence.incrementAndGet();
        String redisKey = "ratelimit:" + consumerKey;

        if (redisTemplate != null) {
            List<String> keys = Collections.singletonList(redisKey);
            return redisTemplate.execute(
                            redisScript,
                            keys,
                            List.of(String.valueOf(now), String.valueOf(windowMs), String.valueOf(limit), member)
                    )
                    .next()
                    .map(raw -> parseResult(raw, limit))
                    .defaultIfEmpty(RateLimitResult.allowed(limit));
        } else {
            return Mono.fromSupplier(() -> executeInMemory(redisKey, now, windowMs, limit, member));
        }
    }

    public Mono<RateLimitResult> isAllowed(String consumerKey, long limit, Duration window) {
        return tryAcquire(consumerKey, limit, window);
    }

    public Mono<RateLimitResult> isAllowed(String consumerKey, long limit, long windowSeconds) {
        return tryAcquire(consumerKey, limit, windowSeconds);
    }

    private RateLimitResult parseResult(Object raw, long configuredLimit) {
        if (raw instanceof List<?> list && list.size() >= 3) {
            long allowed = ((Number) list.get(0)).longValue();
            long retryAfter = ((Number) list.get(1)).longValue();
            long remaining = ((Number) list.get(2)).longValue();
            if (allowed == 1L) {
                return RateLimitResult.allowed(remaining);
            } else {
                return RateLimitResult.rejected(retryAfter);
            }
        }
        return RateLimitResult.allowed(configuredLimit);
    }

    private synchronized RateLimitResult executeInMemory(String key, long now, long windowMs, long limit, String member) {
        NavigableMap<Long, List<String>> windowMap = inMemoryStore.computeIfAbsent(key, k -> new TreeMap<>());

        long clearBefore = now - windowMs;

        // Add
        windowMap.computeIfAbsent(now, k -> new ArrayList<>()).add(member);

        // Trim
        windowMap.headMap(clearBefore, true).clear();

        // Count
        long count = windowMap.values().stream().mapToLong(List::size).sum();

        if (count <= limit) {
            return RateLimitResult.allowed(limit - count);
        } else {
            // Remove the added member
            List<String> currentList = windowMap.get(now);
            if (currentList != null) {
                currentList.remove(member);
                if (currentList.isEmpty()) {
                    windowMap.remove(now);
                }
            }

            long retryAfter = 1;
            if (!windowMap.isEmpty()) {
                long oldestScore = windowMap.firstKey();
                long remainingMs = (oldestScore + windowMs) - now;
                retryAfter = (long) Math.ceil(remainingMs / 1000.0);
                if (retryAfter < 1) {
                    retryAfter = 1;
                }
            } else {
                retryAfter = (long) Math.ceil(windowMs / 1000.0);
            }
            return RateLimitResult.rejected(retryAfter);
        }
    }

    public Clock getClock() {
        return clock;
    }
}
