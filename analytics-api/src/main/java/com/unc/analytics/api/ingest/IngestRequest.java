package com.unc.analytics.api.ingest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

/**
 * Request payload DTO for ingested request-log events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestRequest {

    @JsonProperty("method")
    private String method;

    @JsonProperty("path")
    private String path;

    @JsonAlias({"status", "status_code", "statusCode"})
    @JsonProperty("statusCode")
    @Min(value = 100, message = "Status code must be >= 100")
    @Max(value = 599, message = "Status code must be <= 599")
    private Integer statusCode;

    @JsonAlias({"latency_ms", "latencyMs", "durationMs", "duration_ms", "latency"})
    @JsonProperty("latencyMs")
    @Min(value = 0, message = "Latency must be non-negative")
    private Long latencyMs;

    @JsonAlias({"consumer_id", "consumerId"})
    @JsonProperty("consumerId")
    private String consumerId;

    @JsonAlias({"route_id", "routeId"})
    @JsonProperty("routeId")
    private String routeId;

    @JsonAlias({"service_id", "serviceId"})
    @JsonProperty("serviceId")
    private String serviceId;

    @JsonAlias({"client_ip", "clientIp"})
    @JsonProperty("clientIp")
    private String clientIp;

    @JsonAlias({"request_size", "requestSize"})
    @JsonProperty("requestSize")
    @Min(value = 0, message = "Request size must be non-negative")
    private Long requestSize;

    @JsonAlias({"response_size", "responseSize"})
    @JsonProperty("responseSize")
    @Min(value = 0, message = "Response size must be non-negative")
    private Long responseSize;

    @JsonAlias({"timestamp", "created_at", "occurredAt"})
    @JsonProperty("occurredAt")
    private Instant occurredAt;

    public IngestRequest() {
    }

    public IngestRequest(String method, String path, Integer statusCode, Long latencyMs, String consumerId, Instant occurredAt) {
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.consumerId = consumerId;
        this.occurredAt = occurredAt;
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

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
