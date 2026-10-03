package com.unc.gateway.plugins.integration;

import com.unc.gateway.plugins.RateLimitResult;
import com.unc.gateway.plugins.RedisSlidingWindowRateLimiter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedisSlidingWindowRateLimiter rateLimiter;
    private static ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getFirstMappedPort()
        );
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();

        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext.string();
        redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, serializationContext);

        rateLimiter = new RedisSlidingWindowRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("Redis sliding-window rate limiter: allows N requests, rejects N+1, resets after window elapses")
    void testSlidingWindowQuotaEnforcementAndReset() throws InterruptedException {
        String consumerKey = "consumer-" + UUID.randomUUID();
        long limit = 5;
        long windowSeconds = 2;

        // First 5 requests must all be allowed
        for (int i = 1; i <= limit; i++) {
            StepVerifier.create(rateLimiter.tryAcquire(consumerKey, limit, windowSeconds))
                    .assertNext(result -> {
                        assertThat(result.allowed()).isTrue();
                        assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
                    })
                    .verifyComplete();
        }

        // 6th request within the same 2-second window must be rejected
        StepVerifier.create(rateLimiter.tryAcquire(consumerKey, limit, windowSeconds))
                .assertNext(result -> {
                    assertThat(result.allowed()).isFalse();
                    assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
                })
                .verifyComplete();

        // Wait for the window to elapse (2100ms)
        Thread.sleep(2100);

        // Subsequent request must now be allowed
        StepVerifier.create(rateLimiter.tryAcquire(consumerKey, limit, windowSeconds))
                .assertNext(result -> {
                    assertThat(result.allowed()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Redis sliding-window rate limiter: atomic Lua execution caps concurrent requests at limit N")
    void testConcurrentRequestsCappedAtLimit() {
        String consumerKey = "consumer-concurrent-" + UUID.randomUUID();
        long limit = 5;
        Duration window = Duration.ofSeconds(10);
        int totalRequests = 20;

        List<CompletableFuture<RateLimitResult>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    rateLimiter.tryAcquire(consumerKey, limit, window).block()
            ));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (CompletableFuture<RateLimitResult> future : futures) {
            RateLimitResult result = future.join();
            if (result.allowed()) {
                allowedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
                assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
            }
        }

        assertThat(allowedCount.get()).isEqualTo(5);
        assertThat(rejectedCount.get()).isEqualTo(15);
    }
}
