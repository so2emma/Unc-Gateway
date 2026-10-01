package com.unc.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin configuration payload. Accepts {@code pluginName} as an alias for {@code name}, and
 * {@code order} as an alias for {@code ordering}.
 */
public class PluginConfigDto {

    private UUID id;
    private UUID tenantId;
    private UUID serviceId;
    private UUID routeId;
    private UUID consumerId;

    @JsonAlias({"pluginName", "name"})
    @JsonProperty("name")
    private String name;

    @JsonAlias({"order", "ordering"})
    @JsonProperty("ordering")
    private Integer ordering = 0;

    private Boolean enabled = true;
    private Map<String, Object> config = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public PluginConfigDto() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("pluginName")
    public String getPluginName() {
        return name;
    }

    @JsonProperty("pluginName")
    public void setPluginName(String pluginName) {
        if (pluginName != null && !pluginName.trim().isEmpty()) {
            this.name = pluginName;
        }
    }

    public Integer getOrdering() {
        return ordering;
    }

    public void setOrdering(Integer ordering) {
        this.ordering = ordering;
    }

    @JsonProperty("order")
    public Integer getOrder() {
        return ordering;
    }

    @JsonProperty("order")
    public void setOrder(Integer order) {
        if (order != null) {
            this.ordering = order;
        }
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
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
