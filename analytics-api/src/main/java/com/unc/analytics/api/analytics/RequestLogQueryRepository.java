package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.LatencyMetricsResponse;
import com.unc.analytics.api.analytics.dto.TrafficPulseResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Data access repository for querying aggregated analytics metrics from the shared {@code request_logs} table.
 * <p>
 * Per the Phase 4 tenant isolation rule, every query MUST be strictly scoped by {@code tenant_id}.
 */
public interface RequestLogQueryRepository {

    /**
     * Computes p95 and p99 request latency percentiles for the specified tenant.
     *
     * @param tenantId the verified tenant UUID
     * @return Mono of {@link LatencyMetricsResponse}
     */
    Mono<LatencyMetricsResponse> getLatencyMetrics(UUID tenantId);

    /**
     * Queries recent time-bucketed request volume for the specified tenant covering a default rolling window.
     *
     * @param tenantId the verified tenant UUID
     * @return Flux of {@link TrafficPulseResponse.BucketEntry} in ascending chronological order
     */
    Flux<TrafficPulseResponse.BucketEntry> getTrafficPulse(UUID tenantId);

    /**
     * Queries recent time-bucketed request volume for the specified tenant with a custom maximum bucket limit.
     *
     * @param tenantId the verified tenant UUID
     * @param limit    maximum number of recent buckets to return
     * @return Flux of {@link TrafficPulseResponse.BucketEntry} in ascending chronological order
     */
    Flux<TrafficPulseResponse.BucketEntry> getTrafficPulse(UUID tenantId, int limit);

    /**
     * Convenience method returning the traffic pulse wrapped in {@link TrafficPulseResponse}.
     *
     * @param tenantId the verified tenant UUID
     * @return Mono of {@link TrafficPulseResponse}
     */
    default Mono<TrafficPulseResponse> getTrafficPulseResponse(UUID tenantId) {
        return getTrafficPulse(tenantId).collectList().map(TrafficPulseResponse::new);
    }

    /**
     * Convenience method returning the traffic pulse with limit wrapped in {@link TrafficPulseResponse}.
     *
     * @param tenantId the verified tenant UUID
     * @param limit    maximum number of recent buckets to return
     * @return Mono of {@link TrafficPulseResponse}
     */
    default Mono<TrafficPulseResponse> getTrafficPulseResponse(UUID tenantId, int limit) {
        return getTrafficPulse(tenantId, limit).collectList().map(TrafficPulseResponse::new);
    }
}
