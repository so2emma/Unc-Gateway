package com.unc.analytics.api.ingest;

import com.unc.analytics.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IngestControllerTest {

    private RequestLogRepository requestLogRepository;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        requestLogRepository = mock(RequestLogRepository.class);
        IngestController controller = new IngestController(requestLogRepository);
        TenantContext filter = new TenantContext();

        webTestClient = WebTestClient.bindToController(controller)
                .webFilter(filter)
                .build();
    }

    @Test
    @DisplayName("POST /api/analytics/ingest with valid payload and X-Tenant-Id returns HTTP 201 and passes resolved tenant_id")
    void testIngestValidPayloadReturns201AndPassesTenantId() {
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();

        RequestLog savedRecord = new RequestLog();
        savedRecord.setId(logId);
        savedRecord.setTenantId(tenantId);
        savedRecord.setMethod("GET");
        savedRecord.setPath("/api/v1/echo/hello");
        savedRecord.setStatus(200);
        savedRecord.setLatencyMs(12L);
        savedRecord.setCreatedAt(Instant.now());

        when(requestLogRepository.save(eq(tenantId), any(IngestRequest.class)))
                .thenReturn(Mono.just(savedRecord));

        String requestBody = """
                {
                    "method": "GET",
                    "path": "/api/v1/echo/hello",
                    "statusCode": 200,
                    "latencyMs": 12,
                    "consumerId": "demo-key"
                }
                """;

        webTestClient.post()
                .uri("/api/analytics/ingest")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo(logId.toString())
                .jsonPath("$.status").isEqualTo("created");

        ArgumentCaptor<IngestRequest> requestCaptor = ArgumentCaptor.forClass(IngestRequest.class);
        verify(requestLogRepository, times(1)).save(eq(tenantId), requestCaptor.capture());

        IngestRequest captured = requestCaptor.getValue();
        assertThat(captured.getMethod()).isEqualTo("GET");
        assertThat(captured.getPath()).isEqualTo("/api/v1/echo/hello");
        assertThat(captured.getStatusCode()).isEqualTo(200);
        assertThat(captured.getLatencyMs()).isEqualTo(12L);
        assertThat(captured.getConsumerId()).isEqualTo("demo-key");
    }

    @Test
    @DisplayName("POST /api/analytics/ingest with named tenant string resolves deterministic UUID")
    void testIngestWithNamedTenantString() {
        String tenantName = "tenant-a";
        UUID expectedTenantId = UUID.nameUUIDFromBytes(tenantName.getBytes(StandardCharsets.UTF_8));
        UUID logId = UUID.randomUUID();

        RequestLog savedRecord = new RequestLog();
        savedRecord.setId(logId);
        savedRecord.setTenantId(expectedTenantId);
        savedRecord.setCreatedAt(Instant.now());

        when(requestLogRepository.save(eq(expectedTenantId), any(IngestRequest.class)))
                .thenReturn(Mono.just(savedRecord));

        String requestBody = """
                {
                    "method": "POST",
                    "path": "/api/orders",
                    "statusCode": 201,
                    "latencyMs": 45
                }
                """;

        webTestClient.post()
                .uri("/api/analytics/ingest")
                .header("X-Tenant-Id", tenantName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(logId.toString());

        verify(requestLogRepository).save(eq(expectedTenantId), any(IngestRequest.class));
    }

    @Test
    @DisplayName("POST /api/analytics/ingest missing X-Tenant-Id header returns HTTP 400 and never invokes repository")
    void testIngestMissingTenantHeaderReturns400() {
        String requestBody = """
                {
                    "method": "GET",
                    "path": "/api/v1/test",
                    "statusCode": 200,
                    "latencyMs": 10
                }
                """;

        webTestClient.post()
                .uri("/api/analytics/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(requestLogRepository);
    }

    @Test
    @DisplayName("POST /api/analytics/ingest supports alternative JSON aliases (durationMs, status, routeId)")
    void testIngestSupportsJsonAliases() {
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();

        RequestLog savedRecord = new RequestLog();
        savedRecord.setId(logId);
        savedRecord.setTenantId(tenantId);
        savedRecord.setCreatedAt(Instant.now());

        when(requestLogRepository.save(eq(tenantId), any(IngestRequest.class)))
                .thenReturn(Mono.just(savedRecord));

        String requestBody = """
                {
                    "routeId": "demo-route",
                    "status": 200,
                    "durationMs": 85
                }
                """;

        webTestClient.post()
                .uri("/api/analytics/ingest")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<IngestRequest> captor = ArgumentCaptor.forClass(IngestRequest.class);
        verify(requestLogRepository).save(eq(tenantId), captor.capture());

        IngestRequest captured = captor.getValue();
        assertThat(captured.getRouteId()).isEqualTo("demo-route");
        assertThat(captured.getStatusCode()).isEqualTo(200);
        assertThat(captured.getLatencyMs()).isEqualTo(85L);
    }
}
