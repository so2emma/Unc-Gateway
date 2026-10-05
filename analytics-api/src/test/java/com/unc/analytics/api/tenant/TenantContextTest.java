package com.unc.analytics.api.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private final TenantContext tenantContext = new TenantContext();

    @Test
    @DisplayName("TenantContext extracts valid UUID from X-Tenant-Id header and sets exchange attribute")
    void testExtractValidUuidTenantId() {
        UUID expectedTenantId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/analytics/ingest")
                .header("X-Tenant-Id", expectedTenantId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            UUID tenantId = TenantContext.getTenantId(ex);
            assertThat(tenantId).isEqualTo(expectedTenantId);
            return Mono.empty();
        };

        StepVerifier.create(tenantContext.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        UUID actualAttr = exchange.getAttribute(TenantContext.TENANT_ID_ATTR);
        assertThat(actualAttr).isEqualTo(expectedTenantId);
    }

    @Test
    @DisplayName("TenantContext deterministically maps non-UUID tenant string from X-Tenant-Id header")
    void testExtractNamedTenantString() {
        String tenantName = "tenant-a";
        UUID expectedUuid = UUID.nameUUIDFromBytes(tenantName.getBytes(StandardCharsets.UTF_8));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/analytics/ingest")
                .header("X-Tenant-Id", tenantName)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            UUID tenantId = TenantContext.getTenantId(ex);
            assertThat(tenantId).isEqualTo(expectedUuid);
            return Mono.empty();
        };

        StepVerifier.create(tenantContext.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        UUID actualAttr = exchange.getAttribute(TenantContext.TENANT_ID_ATTR);
        assertThat(actualAttr).isEqualTo(expectedUuid);
    }

    @Test
    @DisplayName("TenantContext rejects request missing X-Tenant-Id header with HTTP 400 before chain execution")
    void testRejectMissingTenantHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/analytics/ingest")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(tenantContext.filter(exchange, chain))
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST
                        && rse.getMessage().contains("X-Tenant-Id header is required"))
                .verify();

        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("TenantContext rejects request with blank X-Tenant-Id header with HTTP 400")
    void testRejectBlankTenantHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/analytics/ingest")
                .header("X-Tenant-Id", "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(tenantContext.filter(exchange, chain))
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();

        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("TenantContext allows non-analytics routes (e.g. /health) without X-Tenant-Id header")
    void testAllowNonAnalyticsEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(tenantContext.filter(exchange, chain))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
    }

    @Test
    @DisplayName("resolveTenantId throws ResponseStatusException 400 when exchange has no tenant")
    void testResolveTenantIdThrowsOnMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/analytics/ingest").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        assertThatThrownBy(() -> TenantContext.resolveTenantId(exchange))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
