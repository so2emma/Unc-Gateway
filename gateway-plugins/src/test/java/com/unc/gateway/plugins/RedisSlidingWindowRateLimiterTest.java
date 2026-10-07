package com.unc.gateway.plugins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSlidingWindowRateLimiterTest {

    static class ControllableClock extends Clock {
        private Instant now;
        private final ZoneId zone;

        public ControllableClock(Instant start) {
            this(start, ZoneOffset.UTC);
        }

        public ControllableClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ControllableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("RedisSlidingWindowRateLimiter: first N calls allowed, N+1th rejected, advances clock and allows again")
    void testSlidingWindowQuotaEnforcementWithControllableClock() {
        Instant startTime = Instant.parse("2026-01-01T00:00:00Z");
        ControllableClock clock = new ControllableClock(startTime);
        RedisSlidingWindowRateLimiter limiter = new RedisSlidingWindowRateLimiter(clock);

        String consumerKey = "consumer-123";
        long limit = 5;
        long windowSeconds = 10; // 10 second window

        // First 5 calls within the 10-second window are allowed
        for (int i = 1; i <= 5; i++) {
            clock.advance(Duration.ofMillis(500)); // +500ms between calls
            StepVerifier.create(limiter.tryAcquire(consumerKey, limit, windowSeconds))
                    .assertNext(res -> {
                        assertThat(res.allowed()).isTrue();
                        assertThat(res.retryAfterSeconds()).isEqualTo(0);
                    })
                    .verifyComplete();
        }

        // 6th call within the same window (time elapsed ~2.5s < 10s) is rejected
        clock.advance(Duration.ofMillis(100));
        StepVerifier.create(limiter.tryAcquire(consumerKey, limit, windowSeconds))
                .assertNext(res -> {
                    assertThat(res.allowed()).isFalse();
                    assertThat(res.retryAfterSeconds()).isGreaterThan(0);
                })
                .verifyComplete();

        // Advance time past the 10-second window boundary from the first call (+9 seconds)
        clock.advance(Duration.ofSeconds(9));

        // Now the oldest calls have fallen outside the window; 7th call is allowed again
        StepVerifier.create(limiter.tryAcquire(consumerKey, limit, windowSeconds))
                .assertNext(res -> {
                    assertThat(res.allowed()).isTrue();
                    assertThat(res.retryAfterSeconds()).isEqualTo(0);
                })
                .verifyComplete();
    }
}
