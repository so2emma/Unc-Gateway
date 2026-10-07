package com.unc.analytics.api.analytics;

import com.unc.analytics.api.analytics.dto.LatencyMetricsResponse;
import com.unc.analytics.api.analytics.dto.TrafficPulseResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * R2DBC reactive implementation of {@link RequestLogQueryRepository}.
 * <p>
 * Enforces the Phase 4 tenant isolation rule on every aggregation query,
 * ensuring query paths can never omit the {@code tenant_id = :tenantId} predicate.
 */
@Repository
public class R2dbcRequestLogQueryRepository implements RequestLogQueryRepository {

    public static final String LATENCY_METRICS_SQL = """
            SELECT
                COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0.0) AS p95,
                COALESCE(percentile_cont(0.99) WITHIN GROUP (ORDER BY latency_ms), 0.0) AS p99
            FROM request_logs
            WHERE tenant_id = :tenantId
            """;

    public static final String TRAFFIC_PULSE_SQL = """
            SELECT bucket_start, request_count
            FROM (
                SELECT date_trunc('minute', created_at) AS bucket_start, COUNT(*) AS request_count
                FROM request_logs
                WHERE tenant_id = :tenantId
                GROUP BY date_trunc('minute', created_at)
                ORDER BY bucket_start DESC
                LIMIT :limit
            ) sub
            ORDER BY bucket_start ASC
            """;

    private final DatabaseClient databaseClient;

    public R2dbcRequestLogQueryRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public String getLatencyMetricsSql() {
        return LATENCY_METRICS_SQL;
    }

    public String getTrafficPulseSql() {
        return TRAFFIC_PULSE_SQL;
    }

    @Override
    public Mono<LatencyMetricsResponse> getLatencyMetrics(UUID tenantId) {
        if (tenantId == null) {
            return Mono.error(new IllegalArgumentException("tenant_id must not be null per tenant isolation rule"));
        }

        return databaseClient.sql(LATENCY_METRICS_SQL)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> {
                    Number p95Num = row.get("p95", Number.class);
                    Number p99Num = row.get("p99", Number.class);
                    Double p95 = p95Num != null ? p95Num.doubleValue() : 0.0;
                    Double p99 = p99Num != null ? p99Num.doubleValue() : 0.0;
                    return new LatencyMetricsResponse(p95, p99);
                })
                .one()
                .defaultIfEmpty(new LatencyMetricsResponse(0.0, 0.0));
    }

    @Override
    public Flux<TrafficPulseResponse.BucketEntry> getTrafficPulse(UUID tenantId) {
        return getTrafficPulse(tenantId, 60);
    }

    @Override
    public Flux<TrafficPulseResponse.BucketEntry> getTrafficPulse(UUID tenantId, int limit) {
        if (tenantId == null) {
            return Flux.error(new IllegalArgumentException("tenant_id must not be null per tenant isolation rule"));
        }
        int effectiveLimit = limit > 0 ? limit : 60;

        return databaseClient.sql(TRAFFIC_PULSE_SQL)
                .bind("tenantId", tenantId)
                .bind("limit", effectiveLimit)
                .map((row, metadata) -> {
                    Object bucketStartObj = row.get("bucket_start");
                    Instant bucketStart = parseInstant(bucketStartObj);
                    Number countNum = row.get("request_count", Number.class);
                    Long requestCount = countNum != null ? countNum.longValue() : 0L;
                    return new TrafficPulseResponse.BucketEntry(bucketStart, requestCount);
                })
                .all();
    }

    private Instant parseInstant(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Instant instant) {
            return instant;
        } else if (obj instanceof OffsetDateTime odt) {
            return odt.toInstant();
        } else if (obj instanceof ZonedDateTime zdt) {
            return zdt.toInstant();
        } else if (obj instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        } else {
            return Instant.parse(obj.toString());
        }
    }
}
