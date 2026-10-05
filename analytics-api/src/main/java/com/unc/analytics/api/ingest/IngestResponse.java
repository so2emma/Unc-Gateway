package com.unc.analytics.api.ingest;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned by the analytics ingestion endpoint upon successful event persistence.
 */
public class IngestResponse {

    private UUID id;
    private String status;
    private Instant timestamp;

    public IngestResponse() {
    }

    public IngestResponse(UUID id, String status) {
        this.id = id;
        this.status = status;
        this.timestamp = Instant.now();
    }

    public IngestResponse(UUID id, String status, Instant timestamp) {
        this.id = id;
        this.status = status;
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
