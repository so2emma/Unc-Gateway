package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.LatencyMetricsResponse;
import com.unc.analytics.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive controller exposing the latency metrics query endpoint (GET /api/analytics/metrics/latency).
 * Returns p95 and p99 request duration percentiles scoped to the caller's verified tenant.
 */
@RestController
@RequestMapping("/api/analytics")
public class LatencyMetricsController {

    private final RequestLogQueryRepository repository;

    public LatencyMetricsController(RequestLogQueryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/metrics/latency")
    public Mono<ResponseEntity<LatencyMetricsResponse>> getLatencyMetrics(
            @RequestHeader(name = TenantContext.TENANT_ID_HEADER, required = false) String tenantHeader,
            ServerWebExchange exchange
    ) {
        UUID tenantId = TenantContext.getTenantId(exchange);
        if (tenantId == null && tenantHeader != null && !tenantHeader.isBlank()) {
            tenantId = TenantContext.parseTenantId(tenantHeader);
        }
        if (tenantId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required"));
        }

        return repository.getLatencyMetrics(tenantId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(new LatencyMetricsResponse(0.0, 0.0)));
    }
}
