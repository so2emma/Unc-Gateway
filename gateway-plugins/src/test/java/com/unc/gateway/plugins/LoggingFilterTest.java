package com.unc.gateway.plugins;

import com.unc.gateway.plugins.api.GatewayFilter;
import com.unc.gateway.plugins.api.GatewayFilterChain;
import com.unc.gateway.plugins.api.PluginConfig;
import com.unc.gateway.plugins.api.PluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFilterTest {

    @Test
    @DisplayName("LoggingFilter.filter: produces exactly one emitted log record containing method, path, status code, and latency")
    void testFilterEmitsOneRecordOnSuccess() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.delay(Duration.ofMillis(25)).then();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getMethod()).isEqualTo("GET");
        assertThat(record.getPath()).isEqualTo("/api/v1/orders/123");
        assertThat(record.getStatusCode()).isEqualTo(200);
        assertThat(record.status()).isEqualTo(200);
        assertThat(record.getLatencyMs()).isGreaterThanOrEqualTo(20);
        assertThat(record.latency()).isEqualTo(record.getLatencyMs());
        assertThat(record.getError()).isNull();
        assertThat(record.getTimestamp()).isNotBlank();
        assertThat(record.getLevel()).isEqualTo("INFO");
    }

    @Test
    @DisplayName("LoggingFilter.filter: non-blocking behavior - Mono completes immediately without waiting on slow log sink")
    void testNonBlockingBehaviorWithSlowSink() throws Exception {
        CountDownLatch sinkStartedLatch = new CountDownLatch(1);
        CountDownLatch sinkReleaseLatch = new CountDownLatch(1);
        CountDownLatch sinkCompletedLatch = new CountDownLatch(1);
        AtomicBoolean sinkFinished = new AtomicBoolean(false);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter.LogSink slowSink = record -> {
            sinkStartedLatch.countDown();
            try {
                // Blocks until released, simulating a slow external log sink / remote transport
                sinkReleaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            captured.add(record);
            sinkFinished.set(true);
            sinkCompletedLatch.countDown();
        };

        LoggingFilter filter = new LoggingFilter(slowSink, Schedulers.boundedElastic());

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/checkout").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.CREATED);
            return Mono.empty();
        };

        long start = System.currentTimeMillis();
        // The Mono<Void> must complete promptly and not block on the slow sink
        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify(Duration.ofMillis(400));
        long duration = System.currentTimeMillis() - start;

        // Verify request completion happened fast (<400ms), while sink is still running in background
        assertThat(duration).isLessThan(400);
        assertThat(sinkFinished.get()).isFalse();

        // Release the slow sink and verify log record was processed asynchronously
        sinkReleaseLatch.countDown();
        assertThat(sinkCompletedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(sinkFinished.get()).isTrue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getStatusCode()).isEqualTo(201);
        assertThat(captured.get(0).getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("LoggingFilter.filter: downstream error produces a log record reflecting error status instead of being dropped")
    void testDownstreamResponseStatusExceptionProducesLogRecord() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/upstream-fail").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain failingChain = ex ->
                Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream service unreachable"));

        StepVerifier.create(filter.filter(exchange, failingChain))
                .expectError(ResponseStatusException.class)
                .verify(Duration.ofSeconds(1));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getStatusCode()).isEqualTo(502);
        assertThat(record.getMethod()).isEqualTo("GET");
        assertThat(record.getPath()).isEqualTo("/api/v1/upstream-fail");
        assertThat(record.getError()).contains("Upstream service unreachable");
    }

    @Test
    @DisplayName("LoggingFilter.filter: downstream unhandled RuntimeException produces 500 log record with error message")
    void testDownstreamGenericExceptionProduces500LogRecord() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.delete("/api/v1/items/42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain failingChain = ex -> Mono.error(new IllegalStateException("Connection pool exhausted"));

        StepVerifier.create(filter.filter(exchange, failingChain))
                .expectError(IllegalStateException.class)
                .verify(Duration.ofSeconds(1));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getStatusCode()).isEqualTo(500);
        assertThat(record.getMethod()).isEqualTo("DELETE");
        assertThat(record.getPath()).isEqualTo("/api/v1/items/42");
        assertThat(record.getError()).isEqualTo("Connection pool exhausted");
    }

    @Test
    @DisplayName("LoggingFilter.filter: captures tenant and consumer identifiers from ConsumerIdentity attribute")
    void testCapturesTenantAndConsumerFromConsumerIdentity() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        ConsumerIdentity identity = new ConsumerIdentity(tenantId, consumerId, UUID.randomUUID(), "prod-key", "alice");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(KeyAuthFilter.ATTR_CONSUMER_IDENTITY, identity);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getTenantId()).isEqualTo(tenantId.toString());
        assertThat(record.getConsumerId()).isEqualTo(consumerId.toString());
    }

    @Test
    @DisplayName("LoggingFilter.filter: captures tenant and consumer identifiers from request headers")
    void testCapturesTenantAndConsumerFromHeaders() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/data")
                .header("X-Tenant-Id", "tenant-alpha")
                .header("X-Consumer-Id", "consumer-beta")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getTenantId()).isEqualTo("tenant-alpha");
        assertThat(record.getConsumerId()).isEqualTo("consumer-beta");
    }

    @Test
    @DisplayName("LoggingFilter.filter: captures traceId and spanId from Reactor ContextView")
    void testCapturesTraceAndSpanFromReactorContext() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/trace-check").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ctx -> ctx.put("traceId", "trace-xyz-987").put("spanId", "span-abc-123"))
        ).verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getTraceId()).isEqualTo("trace-xyz-987");
        assertThat(record.getSpanId()).isEqualTo("span-abc-123");
    }

    @Test
    @DisplayName("LoggingFilter.filter: captures traceId and spanId from W3C traceparent header")
    void testCapturesTraceFromTraceparentHeader() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/w3c")
                .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(record.getSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    @DisplayName("LoggingFilter.filter: captures headers when include_headers is true")
    void testHeadersInclusionWhenEnabled() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<StructuredLogRecord> captured = new CopyOnWriteArrayList<>();

        LoggingFilter filter = new LoggingFilter(record -> {
            captured.add(record);
            latch.countDown();
        });

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/headers")
                .header("X-Custom-Req", "request-header-value")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("plugin_config_logging", Map.of(
                "level", "DEBUG",
                "include_headers", true
        ));

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            ex.getResponse().getHeaders().add("X-Custom-Resp", "response-header-value");
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);

        StructuredLogRecord record = captured.get(0);
        assertThat(record.getLevel()).isEqualTo("DEBUG");
        assertThat(record.requestHeaders()).isNotNull();
        assertThat(record.requestHeaders()).containsEntry("X-Custom-Req", "request-header-value");
        assertThat(record.responseHeaders()).isNotNull();
        assertThat(record.responseHeaders()).containsEntry("X-Custom-Resp", "response-header-value");
    }

    @Test
    @DisplayName("LoggingFilter: auto-configuration registers plugin with schema in PluginRegistry")
    void testAutoConfigurationRegistersLoggingPlugin() {
        PluginRegistry registry = new PluginRegistry();
        LoggingFilter filter = new LoggingFilter();

        GatewayPluginsAutoConfiguration.PluginRegistrationInitializer initializer =
                new GatewayPluginsAutoConfiguration.PluginRegistrationInitializer(
                        registry,
                        null,
                        null,
                        null,
                        null,
                        filter
                );

        assertThat(registry.isRegistered("logging")).isTrue();
        GatewayFilter registered = registry.getFilter("logging");
        assertThat(registered).isSameAs(filter);
        assertThat(registered.getName()).isEqualTo("logging");

        // Validate valid config passes
        PluginConfig validConfig = new PluginConfig("1", "t1", "logging", 1, true, Map.of("level", "INFO"));
        registry.validateConfig(validConfig);

        // Validate invalid config is rejected
        PluginConfig invalidConfig = new PluginConfig("2", "t1", "logging", 1, true, Map.of("level", "INVALID"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.validateConfig(invalidConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported 'level' value 'INVALID'");
    }

    @Test
    @DisplayName("LoggingFilter: default constructor works with default sink without throwing")
    void testDefaultSinkExecution() {
        LoggingFilter filter = new LoggingFilter();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/default-sink").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }
}
