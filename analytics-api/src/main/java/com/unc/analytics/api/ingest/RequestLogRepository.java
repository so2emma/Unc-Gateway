package com.unc.analytics.api.ingest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Data access repository for {@link RequestLog} events.
 * <p>
 * Per the Phase 4 tenant isolation rule, every write and read operation against the shared
 * {@code request_logs} table MUST be explicitly scoped by {@code tenant_id}.
 */
public interface RequestLogRepository {

    /**
     * Persists an ingested request-log event for the given tenant.
     *
     * @param tenantId the verified tenant UUID
     * @param request  the ingestion event payload
     * @return Mono of the persisted {@link RequestLog}
     */
    Mono<RequestLog> save(UUID tenantId, IngestRequest request);

    /**
     * Persists a pre-constructed {@link RequestLog} entity.
     * The entity's {@code tenantId} must not be null.
     *
     * @param record the entity to persist
     * @return Mono of the persisted entity
     */
    Mono<RequestLog> save(RequestLog record);

    /**
     * Queries all request logs belonging to the specified tenant.
     *
     * @param tenantId the owning tenant UUID
     * @return Flux of {@link RequestLog} rows
     */
    Flux<RequestLog> findByTenantId(UUID tenantId);

    /**
     * Queries a specific request log by ID strictly scoped to the specified tenant.
     *
     * @param id       the record ID
     * @param tenantId the owning tenant UUID
     * @return Mono of the matching {@link RequestLog}, or empty if not found
     */
    Mono<RequestLog> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Counts the total number of request logs for the specified tenant.
     *
     * @param tenantId the owning tenant UUID
     * @return Mono of the row count
     */
    Mono<Long> countByTenantId(UUID tenantId);
}
