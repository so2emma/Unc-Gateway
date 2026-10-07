package com.unc.analytics.api.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Response DTO for the latency metrics query endpoint (GET /api/analytics/metrics/latency).
 * Carries p95 and p99 request duration percentiles in milliseconds.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LatencyMetricsResponse {

    @JsonProperty("p95")
    private Double p95;

    @JsonProperty("p99")
    private Double p99;

    public LatencyMetricsResponse() {
    }

    public LatencyMetricsResponse(Double p95, Double p99) {
        this.p95 = p95;
        this.p99 = p99;
    }

    public LatencyMetricsResponse(Number p95, Number p99) {
        this.p95 = p95 != null ? p95.doubleValue() : 0.0;
        this.p99 = p99 != null ? p99.doubleValue() : 0.0;
    }

    public Double getP95() {
        return p95;
    }

    public void setP95(Double p95) {
        this.p95 = p95;
    }

    public Double getP99() {
        return p99;
    }

    public void setP99(Double p99) {
        this.p99 = p99;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LatencyMetricsResponse that = (LatencyMetricsResponse) o;
        return Objects.equals(p95, that.p95) && Objects.equals(p99, that.p99);
    }

    @Override
    public int hashCode() {
        return Objects.hash(p95, p99);
    }

    @Override
    public String toString() {
        return "LatencyMetricsResponse{" +
                "p95=" + p95 +
                ", p99=" + p99 +
                '}';
    }
}
