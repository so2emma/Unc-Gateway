package com.unc.analytics.api.ingest;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity mapping rows in the shared {@code request_logs} PostgreSQL table.
 */
@Table("request_logs")
public class RequestLog {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("service_id")
    private UUID serviceId;

    @Column("route_id")
    private UUID routeId;

    @Column("consumer_id")
    private UUID consumerId;

    @Column("client_ip")
    private String clientIp;

    @Column("method")
    private String method;

    @Column("path")
    private String path;

    @Column("status")
    private Integer status;

    @Column("latency_ms")
    private Long latencyMs;

    @Column("request_size")
    private Long requestSize;

    @Column("response_size")
    private Long responseSize;

    @Column("created_at")
    private Instant createdAt;

    public RequestLog() {
    }

    public RequestLog(
            UUID id,
            UUID tenantId,
            UUID serviceId,
            UUID routeId,
            UUID consumerId,
            String clientIp,
            String method,
            String path,
            Integer status,
            Long latencyMs,
            Long requestSize,
            Long responseSize,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.routeId = routeId;
        this.consumerId = consumerId;
        this.clientIp = clientIp;
        this.method = method;
        this.path = path;
        this.status = status;
        this.latencyMs = latencyMs;
        this.requestSize = requestSize;
        this.responseSize = responseSize;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(UUID consumerId) {
        this.consumerId = consumerId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getRequestSize() {
        return requestSize;
    }

    public void setRequestSize(Long requestSize) {
        this.requestSize = requestSize;
    }

    public Long getResponseSize() {
        return responseSize;
    }

    public void setResponseSize(Long responseSize) {
        this.responseSize = responseSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
