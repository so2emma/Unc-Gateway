package com.unc.analytics.api.analytics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for the traffic pulse query endpoint (GET /api/analytics/traffic-pulse).
 * Carries an ordered array of recent time-bucketed request volume entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrafficPulseResponse {

    @JsonProperty("buckets")
    @JsonAlias({"entries", "data", "items"})
    private List<BucketEntry> buckets;

    public TrafficPulseResponse() {
        this.buckets = new ArrayList<>();
    }

    @JsonCreator
    public TrafficPulseResponse(@JsonProperty("buckets") List<BucketEntry> buckets) {
        this.buckets = buckets != null ? new ArrayList<>(buckets) : new ArrayList<>();
    }

    public List<BucketEntry> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<BucketEntry> buckets) {
        this.buckets = buckets != null ? new ArrayList<>(buckets) : new ArrayList<>();
    }

    public void addBucket(BucketEntry entry) {
        if (this.buckets == null) {
            this.buckets = new ArrayList<>();
        }
        this.buckets.add(entry);
    }

    /**
     * Entry representing a single time-bucketed traffic pulse aggregation point.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BucketEntry {

        @JsonProperty("bucketStart")
        @JsonAlias({"start", "timestamp", "time"})
        private Instant bucketStart;

        @JsonProperty("requestCount")
        @JsonAlias({"count", "requests", "total"})
        private Long requestCount;

        public BucketEntry() {
        }

        public BucketEntry(Instant bucketStart, Long requestCount) {
            this.bucketStart = bucketStart;
            this.requestCount = requestCount != null ? requestCount : 0L;
        }

        public BucketEntry(Instant bucketStart, Number requestCount) {
            this.bucketStart = bucketStart;
            this.requestCount = requestCount != null ? requestCount.longValue() : 0L;
        }

        public Instant getBucketStart() {
            return bucketStart;
        }

        public void setBucketStart(Instant bucketStart) {
            this.bucketStart = bucketStart;
        }

        public Long getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(Long requestCount) {
            this.requestCount = requestCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BucketEntry that = (BucketEntry) o;
            return Objects.equals(bucketStart, that.bucketStart) &&
                    Objects.equals(requestCount, that.requestCount);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucketStart, requestCount);
        }

        @Override
        public String toString() {
            return "BucketEntry{" +
                    "bucketStart=" + bucketStart +
                    ", requestCount=" + requestCount +
                    '}';
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrafficPulseResponse that = (TrafficPulseResponse) o;
        return Objects.equals(buckets, that.buckets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buckets);
    }

    @Override
    public String toString() {
        return "TrafficPulseResponse{" +
                "buckets=" + buckets +
                '}';
    }
}
