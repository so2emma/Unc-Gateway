package com.unc.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public class ServiceDto {

    private String id;
    private String tenantId;
    private String name;

    @JsonAlias({"upstreamUrl", "url"})
    @JsonProperty("url")
    private String url;

    private Integer connectTimeout = 6000;
    private Integer readTimeout = 60000;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public ServiceDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("upstreamUrl")
    public String getUpstreamUrl() {
        return url;
    }

    @JsonProperty("upstreamUrl")
    public void setUpstreamUrl(String upstreamUrl) {
        if (upstreamUrl != null && !upstreamUrl.trim().isEmpty()) {
            this.url = upstreamUrl;
        }
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
