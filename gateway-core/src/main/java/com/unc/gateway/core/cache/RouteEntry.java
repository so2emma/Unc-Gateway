package com.unc.gateway.core.cache;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record holding one resolved route's matchable path, tenant_id,
 * and upstream target (host/base URL), materialized from a joined services/routes row.
 */
public record RouteEntry(
        UUID routeId,
        UUID serviceId,
        UUID tenantId,
        String path,
        String upstreamUrl,
        boolean stripPath
) {
    public RouteEntry {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(upstreamUrl, "upstreamUrl cannot be null");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
    }

    public RouteEntry(String path, UUID tenantId, String upstreamUrl) {
        this(UUID.randomUUID(), UUID.randomUUID(), tenantId, path, upstreamUrl, true);
    }

    public RouteEntry(UUID routeId, UUID serviceId, UUID tenantId, String path, String upstreamUrl) {
        this(routeId, serviceId, tenantId, path, upstreamUrl, true);
    }

    public UUID getRouteId() {
        return routeId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPath() {
        return path;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public boolean isStripPath() {
        return stripPath;
    }
}
