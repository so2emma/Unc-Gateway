package com.unc.gateway.core.listen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reconnect and backoff policy for PostgreSQL LISTEN connections.
 * Automatically triggers reconnection and re-subscribes to channels
 * when the listening connection drops.
 */
@Component
public class ListenerReconnectionPolicy {

    private static final Logger log = LoggerFactory.getLogger(ListenerReconnectionPolicy.class);

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final long maxAttempts;

    public ListenerReconnectionPolicy() {
        this(Duration.ofMillis(500), Duration.ofSeconds(30), Long.MAX_VALUE);
    }

    public ListenerReconnectionPolicy(
            @Value("${gateway.listener.reconnect.initial-delay:500ms}") Duration initialDelay,
            @Value("${gateway.listener.reconnect.max-delay:30s}") Duration maxDelay,
            @Value("${gateway.listener.reconnect.max-attempts:-1}") long maxAttempts
    ) {
        this.initialDelay = initialDelay != null ? initialDelay : Duration.ofMillis(500);
        this.maxDelay = maxDelay != null ? maxDelay : Duration.ofSeconds(30);
        this.maxAttempts = maxAttempts <= 0 ? Long.MAX_VALUE : maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public long getMaxAttempts() {
        return maxAttempts;
    }

    public Retry createRetrySpec() {
        return createRetrySpec(null);
    }

    public Retry createRetrySpec(Consumer<Retry.RetrySignal> onRetry) {
        RetryBackoffSpec spec = Retry.backoff(maxAttempts, initialDelay)
                .maxBackoff(maxDelay)
                .transientErrors(true)
                .doBeforeRetry(signal -> {
                    log.warn("LISTEN connection lost (attempt {}). Reconnecting with backoff... Reason: {}",
                            signal.totalRetries() + 1, signal.failure().getMessage());
                    if (onRetry != null) {
                        onRetry.accept(signal);
                    }
                });
        return spec;
    }

    /**
     * Executes the connection/listen action in a resilient flux that automatically reconnects
     * and re-invokes the supplier when the underlying stream fails or terminates with an error.
     *
     * @param connectAndListenAction supplier providing the stream that connects and listens
     * @param <T> element type
     * @return resilient Flux that reconnects indefinitely (or up to maxAttempts)
     */
    public <T> Flux<T> resilientStream(Supplier<Flux<T>> connectAndListenAction) {
        return Flux.defer(connectAndListenAction)
                .repeat()
                .retryWhen(createRetrySpec());
    }

    /**
     * Applies the retry/reconnection policy to an existing Flux.
     *
     * @param flux input Flux
     * @param <T> element type
     * @return Flux with retryWhen applied
     */
    public <T> Flux<T> apply(Flux<T> flux) {
        return flux.retryWhen(createRetrySpec());
    }
}
