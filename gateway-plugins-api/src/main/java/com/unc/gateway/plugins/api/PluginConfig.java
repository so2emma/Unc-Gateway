package com.unc.gateway.plugins.api;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a tenant-scoped plugin configuration rule.
 */
public class PluginConfig {
    private String id;
    private String tenantId;
    private String name;
    private int order;
    private boolean enabled = true;
    private Map<String, Object> config;

    public PluginConfig() {
    }

    public PluginConfig(String id, String tenantId, String name, int order, boolean enabled, Map<String, Object> config) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.order = order;
        this.enabled = enabled;
        this.config = config;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginConfig that = (PluginConfig) o;
        return order == that.order &&
                enabled == that.enabled &&
                Objects.equals(id, that.id) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name, order, enabled, config);
    }

    @Override
    public String toString() {
        return "PluginConfig{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", order=" + order +
                ", enabled=" + enabled +
                ", config=" + config +
                '}';
    }
}
