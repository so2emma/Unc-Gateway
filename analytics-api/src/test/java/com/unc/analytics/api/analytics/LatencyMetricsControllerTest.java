package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.LatencyMetricsResponse;
import com.unc.analytics.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LatencyMetricsControllerTest {

    private RequestLogQueryRepository repository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        repository = mock(RequestLogQueryRepository.class);
        LatencyMetricsController controller = new LatencyMetricsController(repository);
        TenantContext filter = new TenantContext();

        webTestClient = WebTestClient.bindToController(controller)
                .webFilter(filter)
                .build();
    }

    @Test
    @DisplayName("GET /api/analytics/metrics/latency returns HTTP 200 with unchanged p95/p99 for resolved tenant")
    void testGetLatencyMetricsPassesThroughUnchanged() {
        UUID tenantId = UUID.randomUUID();
        double expectedP95 = 22.5;
        double expectedP99 = 412.0;

        when(repository.getLatencyMetrics(eq(tenantId)))
                .thenReturn(Mono.just(new LatencyMetricsResponse(expectedP95, expectedP99)));

        webTestClient.get()
                .uri("/api/analytics/metrics/latency")
                .header("X-Tenant-Id", tenantId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(LatencyMetricsResponse.class)
                .value(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getP95()).isEqualTo(expectedP95);
                    assertThat(response.getP99()).isEqualTo(expectedP99);
                });

        verify(repository, times(1)).getLatencyMetrics(eq(tenantId));
    }

    @Test
    @DisplayName("GET /api/analytics/metrics/latency with named tenant string resolves deterministic UUID")
    void testGetLatencyMetricsWithNamedTenantString() {
        String tenantName = "tenant-a";
        UUID expectedTenantId = UUID.nameUUIDFromBytes(tenantName.getBytes(StandardCharsets.UTF_8));

        when(repository.getLatencyMetrics(eq(expectedTenantId)))
                .thenReturn(Mono.just(new LatencyMetricsResponse(15.0, 95.0)));

        webTestClient.get()
                .uri("/api/analytics/metrics/latency")
                .header("X-Tenant-Id", tenantName)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.p95").isEqualTo(15.0)
                .jsonPath("$.p99").isEqualTo(95.0);

        verify(repository).getLatencyMetrics(eq(expectedTenantId));
    }

    @Test
    @DisplayName("GET /api/analytics/metrics/latency missing X-Tenant-Id returns HTTP 400 and never invokes repository")
    void testGetLatencyMetricsMissingTenantReturns400() {
        webTestClient.get()
                .uri("/api/analytics/metrics/latency")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("GET /api/analytics/metrics/latency handles empty repository result with zero defaults")
    void testGetLatencyMetricsEmptyResultDefaults() {
        UUID tenantId = UUID.randomUUID();

        when(repository.getLatencyMetrics(eq(tenantId)))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/analytics/metrics/latency")
                .header("X-Tenant-Id", tenantId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.p95").isEqualTo(0.0)
                .jsonPath("$.p99").isEqualTo(0.0);

        verify(repository).getLatencyMetrics(eq(tenantId));
    }
}
