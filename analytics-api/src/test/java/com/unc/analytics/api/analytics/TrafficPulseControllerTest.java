package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.TrafficPulseResponse;
import com.unc.analytics.api.analytics.dto.TrafficPulseResponse.BucketEntry;
import com.unc.analytics.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrafficPulseControllerTest {

    private RequestLogQueryRepository repository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        repository = mock(RequestLogQueryRepository.class);
        TrafficPulseController controller = new TrafficPulseController(repository);
        TenantContext filter = new TenantContext();

        webTestClient = WebTestClient.bindToController(controller)
                .webFilter(filter)
                .build();
    }

    @Test
    @DisplayName("GET /api/analytics/traffic-pulse preserves bucket order and values for resolved tenant")
    void testGetTrafficPulsePreservesBucketOrderAndValues() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant t1 = now.minus(2, ChronoUnit.MINUTES);
        Instant t2 = now.minus(1, ChronoUnit.MINUTES);
        Instant t3 = now;

        BucketEntry b1 = new BucketEntry(t1, 5L);
        BucketEntry b2 = new BucketEntry(t2, 12L);
        BucketEntry b3 = new BucketEntry(t3, 20L);

        when(repository.getTrafficPulse(eq(tenantId)))
                .thenReturn(Flux.just(b1, b2, b3));

        webTestClient.get()
                .uri("/api/analytics/traffic-pulse")
                .header("X-Tenant-Id", tenantId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(TrafficPulseResponse.class)
                .value(response -> {
                    assertThat(response).isNotNull();
                    List<BucketEntry> buckets = response.getBuckets();
                    assertThat(buckets).hasSize(3);

                    assertThat(buckets.get(0).getBucketStart()).isEqualTo(t1);
                    assertThat(buckets.get(0).getRequestCount()).isEqualTo(5L);

                    assertThat(buckets.get(1).getBucketStart()).isEqualTo(t2);
                    assertThat(buckets.get(1).getRequestCount()).isEqualTo(12L);

                    assertThat(buckets.get(2).getBucketStart()).isEqualTo(t3);
                    assertThat(buckets.get(2).getRequestCount()).isEqualTo(20L);
                });

        verify(repository, times(1)).getTrafficPulse(eq(tenantId));
    }

    @Test
    @DisplayName("GET /api/analytics/traffic-pulse with named tenant string resolves deterministic UUID")
    void testGetTrafficPulseWithNamedTenantString() {
        String tenantName = "tenant-a";
        UUID expectedTenantId = UUID.nameUUIDFromBytes(tenantName.getBytes(StandardCharsets.UTF_8));
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        when(repository.getTrafficPulse(eq(expectedTenantId)))
                .thenReturn(Flux.just(new BucketEntry(t1, 10L)));

        webTestClient.get()
                .uri("/api/analytics/traffic-pulse")
                .header("X-Tenant-Id", tenantName)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buckets[0].requestCount").isEqualTo(10);

        verify(repository).getTrafficPulse(eq(expectedTenantId));
    }

    @Test
    @DisplayName("GET /api/analytics/traffic-pulse missing X-Tenant-Id returns HTTP 400 and never invokes repository")
    void testGetTrafficPulseMissingTenantReturns400() {
        webTestClient.get()
                .uri("/api/analytics/traffic-pulse")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("GET /api/analytics/traffic-pulse handles empty repository result with empty buckets array")
    void testGetTrafficPulseEmptyResult() {
        UUID tenantId = UUID.randomUUID();

        when(repository.getTrafficPulse(eq(tenantId)))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/analytics/traffic-pulse")
                .header("X-Tenant-Id", tenantId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.buckets").isArray()
                .jsonPath("$.buckets.length()").isEqualTo(0);

        verify(repository).getTrafficPulse(eq(tenantId));
    }
}
