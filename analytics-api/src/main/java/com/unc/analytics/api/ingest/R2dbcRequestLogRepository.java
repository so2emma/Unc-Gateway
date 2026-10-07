package com.unc.analytics.api.ingest;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * R2DBC implementation of {@link RequestLogRepository}.
 * <p>
 * Enforces the Phase 4 tenant isolation rule on every read and write operation.
 */
@Repository
public class R2dbcRequestLogRepository implements RequestLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO request_logs (
                id, tenant_id, service_id, route_id, consumer_id,
                client_ip, method, path, status, latency_ms,
                request_size, response_size, created_at
            ) VALUES (
                :id, :tenantId, :serviceId, :routeId, :consumerId,
                :clientIp, :method, :path, :status, :latencyMs,
                :requestSize, :responseSize, :createdAt
            )
            """;

    private static final String SELECT_BY_TENANT_SQL = """
            SELECT id, tenant_id, service_id, route_id, consumer_id,
                   client_ip, method, path, status, latency_ms,
                   request_size, response_size, created_at
            FROM request_logs
            WHERE tenant_id = :tenantId
            ORDER BY created_at DESC
            """;

    private static final String SELECT_BY_ID_AND_TENANT_SQL = """
            SELECT id, tenant_id, service_id, route_id, consumer_id,
                   client_ip, method, path, status, latency_ms,
                   request_size, response_size, created_at
            FROM request_logs
            WHERE id = :id AND tenant_id = :tenantId
            """;

    private static final String COUNT_BY_TENANT_SQL = """
            SELECT COUNT(*) AS total
            FROM request_logs
            WHERE tenant_id = :tenantId
            """;

    private final DatabaseClient databaseClient;

    public R2dbcRequestLogRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<RequestLog> save(UUID tenantId, IngestRequest request) {
        if (tenantId == null) {
            return Mono.error(new IllegalArgumentException("tenant_id must not be null per tenant isolation rule"));
        }
        if (request == null) {
            return Mono.error(new IllegalArgumentException("IngestRequest must not be null"));
        }

        RequestLog record = new RequestLog();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setServiceId(parseUuidOrNull(request.getServiceId()));
        record.setRouteId(parseUuidOrNull(request.getRouteId()));
        record.setConsumerId(parseUuidOrNull(request.getConsumerId()));
        record.setClientIp(request.getClientIp());
        record.setMethod(request.getMethod() != null ? request.getMethod() : "GET");
        record.setPath(request.getPath() != null ? request.getPath() : "/");
        record.setStatus(request.getStatusCode() != null ? request.getStatusCode() : 200);
        record.setLatencyMs(request.getLatencyMs() != null ? request.getLatencyMs() : 0L);
        record.setRequestSize(request.getRequestSize());
        record.setResponseSize(request.getResponseSize());
        record.setCreatedAt(request.getOccurredAt() != null ? request.getOccurredAt() : Instant.now());

        return save(record);
    }

    @Override
    public Mono<RequestLog> save(RequestLog record) {
        if (record == null) {
            return Mono.error(new IllegalArgumentException("RequestLog record must not be null"));
        }
        if (record.getTenantId() == null) {
            return Mono.error(new IllegalArgumentException("tenant_id must not be null per tenant isolation rule"));
        }

        if (record.getId() == null) {
            record.setId(UUID.randomUUID());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
        if (record.getMethod() == null) {
            record.setMethod("GET");
        }
        if (record.getPath() == null) {
            record.setPath("/");
        }
        if (record.getStatus() == null) {
            record.setStatus(200);
        }
        if (record.getLatencyMs() == null) {
            record.setLatencyMs(0L);
        }

        OffsetDateTime createdAtTz = OffsetDateTime.ofInstant(record.getCreatedAt(), ZoneOffset.UTC);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_SQL)
                .bind("id", record.getId())
                .bind("tenantId", record.getTenantId())
                .bind("method", record.getMethod())
                .bind("path", record.getPath())
                .bind("status", record.getStatus())
                .bind("latencyMs", record.getLatencyMs())
                .bind("createdAt", createdAtTz);

        spec = bindOrNull(spec, "serviceId", record.getServiceId(), UUID.class);
        spec = bindOrNull(spec, "routeId", record.getRouteId(), UUID.class);
        spec = bindOrNull(spec, "consumerId", record.getConsumerId(), UUID.class);
        spec = bindOrNull(spec, "clientIp", record.getClientIp(), String.class);
        spec = bindOrNull(spec, "requestSize", record.getRequestSize(), Long.class);
        spec = bindOrNull(spec, "responseSize", record.getResponseSize(), Long.class);

        return spec.fetch().rowsUpdated().thenReturn(record);
    }

    @Override
    public Flux<RequestLog> findByTenantId(UUID tenantId) {
        if (tenantId == null) {
            return Flux.error(new IllegalArgumentException("tenant_id must not be null for tenant-scoped read"));
        }

        return databaseClient.sql(SELECT_BY_TENANT_SQL)
                .bind("tenantId", tenantId)
                .map(this::mapRow)
                .all();
    }

    @Override
    public Mono<RequestLog> findByIdAndTenantId(UUID id, UUID tenantId) {
        if (id == null || tenantId == null) {
            return Mono.error(new IllegalArgumentException("id and tenant_id must not be null"));
        }

        return databaseClient.sql(SELECT_BY_ID_AND_TENANT_SQL)
                .bind("id", id)
                .bind("tenantId", tenantId)
                .map(this::mapRow)
                .one();
    }

    @Override
    public Mono<Long> countByTenantId(UUID tenantId) {
        if (tenantId == null) {
            return Mono.error(new IllegalArgumentException("tenant_id must not be null for tenant-scoped count"));
        }

        return databaseClient.sql(COUNT_BY_TENANT_SQL)
                .bind("tenantId", tenantId)
                .map((row, metadata) -> {
                    Number total = row.get("total", Number.class);
                    return total != null ? total.longValue() : 0L;
                })
                .one()
                .defaultIfEmpty(0L);
    }

    private <T> DatabaseClient.GenericExecuteSpec bindOrNull(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            T value,
            Class<T> type
    ) {
        if (value != null) {
            return spec.bind(name, value);
        } else {
            return spec.bindNull(name, type);
        }
    }

    private RequestLog mapRow(Row row, RowMetadata metadata) {
        RequestLog log = new RequestLog();
        log.setId(row.get("id", UUID.class));
        log.setTenantId(row.get("tenant_id", UUID.class));
        log.setServiceId(row.get("service_id", UUID.class));
        log.setRouteId(row.get("route_id", UUID.class));
        log.setConsumerId(row.get("consumer_id", UUID.class));
        log.setClientIp(row.get("client_ip", String.class));
        log.setMethod(row.get("method", String.class));
        log.setPath(row.get("path", String.class));
        log.setStatus(row.get("status", Integer.class));

        Number latency = row.get("latency_ms", Number.class);
        log.setLatencyMs(latency != null ? latency.longValue() : null);

        Number reqSize = row.get("request_size", Number.class);
        log.setRequestSize(reqSize != null ? reqSize.longValue() : null);

        Number respSize = row.get("response_size", Number.class);
        log.setResponseSize(respSize != null ? respSize.longValue() : null);

        Object createdObj = row.get("created_at");
        if (createdObj instanceof Instant instant) {
            log.setCreatedAt(instant);
        } else if (createdObj instanceof OffsetDateTime odt) {
            log.setCreatedAt(odt.toInstant());
        } else if (createdObj instanceof ZonedDateTime zdt) {
            log.setCreatedAt(zdt.toInstant());
        } else if (createdObj instanceof LocalDateTime ldt) {
            log.setCreatedAt(ldt.toInstant(ZoneOffset.UTC));
        } else if (createdObj != null) {
            log.setCreatedAt(Instant.parse(createdObj.toString()));
        }

        return log;
    }

    private UUID parseUuidOrNull(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        String trimmed = str.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
