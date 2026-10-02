package com.unc.gateway.core.listen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 12: ListenerReconnectionPolicy Unit Tests")
class ListenerReconnectionPolicyTest {

    @Test
    @DisplayName("given a simulated connection-drop signal, verifies the policy triggers a re-LISTEN attempt on all channels")
    void testConnectionDropTriggersReconnection() {
        // Configure policy with immediate retry for fast unit testing
        ListenerReconnectionPolicy policy = new ListenerReconnectionPolicy(
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                5
        );

        AtomicInteger connectAttempts = new AtomicInteger(0);

        // Simulated connect-and-listen action: fails on first attempt with connection-drop exception, succeeds on second
        Flux<String> resilientStream = policy.resilientStream(() -> {
            int attempt = connectAttempts.incrementAndGet();
            if (attempt == 1) {
                return Flux.error(new IOException("Simulated PostgreSQL connection reset"));
            }
            return Flux.just("LISTEN services_changed", "LISTEN routes_changed", "LISTEN plugin_configs_changed");
        });

        StepVerifier.create(resilientStream.take(3))
                .expectNext("LISTEN services_changed")
                .expectNext("LISTEN routes_changed")
                .expectNext("LISTEN plugin_configs_changed")
                .verifyComplete();

        // Confirms reconnection was executed after connection drop
        assertThat(connectAttempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("verifies configurable backoff settings and retry properties")
    void testPolicyConfiguration() {
        ListenerReconnectionPolicy policy = new ListenerReconnectionPolicy(
                Duration.ofMillis(250),
                Duration.ofSeconds(15),
                10
        );

        assertThat(policy.getInitialDelay()).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.getMaxDelay()).isEqualTo(Duration.ofSeconds(15));
        assertThat(policy.getMaxAttempts()).isEqualTo(10);
        assertThat(policy.createRetrySpec()).isNotNull();
    }
}
