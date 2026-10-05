package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.TrafficPulseResponse;
import com.unc.analytics.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive controller exposing the traffic pulse query endpoint (GET /api/analytics/traffic-pulse).
 * Returns an ordered array of recent time-bucketed request volume entries scoped to the caller's verified tenant.
 */
@RestController
@RequestMapping("/api/analytics")
public class TrafficPulseController {

    private final RequestLogQueryRepository repository;

    public TrafficPulseController(RequestLogQueryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/traffic-pulse")
    public Mono<ResponseEntity<TrafficPulseResponse>> getTrafficPulse(
            @RequestHeader(name = TenantContext.TENANT_ID_HEADER, required = false) String tenantHeader,
            @RequestParam(name = "limit", required = false, defaultValue = "60") int limit,
            ServerWebExchange exchange
    ) {
        UUID tenantId = TenantContext.getTenantId(exchange);
        if (tenantId == null && tenantHeader != null && !tenantHeader.isBlank()) {
            tenantId = TenantContext.parseTenantId(tenantHeader);
        }
        if (tenantId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required"));
        }

        Flux<TrafficPulseResponse.BucketEntry> pulseFlux = (limit > 0 && limit != 60)
                ? repository.getTrafficPulse(tenantId, limit)
                : repository.getTrafficPulse(tenantId);

        return pulseFlux
                .collectList()
                .map(buckets -> ResponseEntity.ok(new TrafficPulseResponse(buckets)))
                .defaultIfEmpty(ResponseEntity.ok(new TrafficPulseResponse()));
    }
}
