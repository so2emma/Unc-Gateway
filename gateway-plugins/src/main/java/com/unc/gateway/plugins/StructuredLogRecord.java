package com.unc.gateway.plugins;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Structured log record emitted asynchronously upon request completion by {@link LoggingFilter}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StructuredLogRecord(
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("method") String method,
        @JsonProperty("path") String path,
        @JsonProperty("statusCode") int statusCode,
        @JsonProperty("latencyMs") long latencyMs,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("consumerId") String consumerId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("spanId") String spanId,
        @JsonProperty("level") String level,
        @JsonProperty("requestHeaders") Map<String, String> requestHeaders,
        @JsonProperty("responseHeaders") Map<String, String> responseHeaders,
        @JsonProperty("error") String error
) {

    public String getTimestamp() {
        return timestamp;
    }

    @JsonProperty("status")
    public int status() {
        return statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getStatus() {
        return statusCode;
    }

    @JsonProperty("latency")
    public long latency() {
        return latencyMs;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public long getLatency() {
        return latencyMs;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getLevel() {
        return level;
    }

    public String getError() {
        return error;
    }
}
