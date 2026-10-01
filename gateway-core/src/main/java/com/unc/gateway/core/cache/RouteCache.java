package com.unc.gateway.core.cache;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory cache exposing lookup-by-path-and-tenant,
 * single-entry mutations, and bulk-replace operations.
 */
@Component
public class RouteCache {

    public record RouteKey(String path, UUID tenantId) {
        public RouteKey {
            path = normalize(path);
        }

        private static String normalize(String p) {
            if (p == null || p.isBlank()) {
                return "/";
            }
            String trimmed = p.trim();
            if (!trimmed.startsWith("/")) {
                trimmed = "/" + trimmed;
            }
            if (trimmed.length() > 1 && trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    private final ConcurrentMap<RouteKey, RouteEntry> routes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RouteKey> routeIdIndex = new ConcurrentHashMap<>();

    /**
     * Replaces the entire route cache with the provided collection of entries atomically.
     *
     * @param entries new collection of route entries
     */
    public synchronized void bulkReplace(Collection<RouteEntry> entries) {
        routes.clear();
        routeIdIndex.clear();
        if (entries != null) {
            for (RouteEntry entry : entries) {
                put(entry);
            }
        }
    }

    /**
     * Adds or updates a single route entry in the cache.
     *
     * @param entry the route entry to put
     */
    public void put(RouteEntry entry) {
        if (entry == null) {
            return;
        }
        RouteKey key = new RouteKey(entry.path(), entry.tenantId());
        routes.put(key, entry);
        if (entry.routeId() != null) {
            routeIdIndex.put(entry.routeId(), key);
        }
    }

    /**
     * Evicts a route entry by its route ID.
     *
     * @param routeId the route ID to evict
     */
    public void evict(UUID routeId) {
        if (routeId == null) {
            return;
        }
        RouteKey key = routeIdIndex.remove(routeId);
        if (key != null) {
            routes.remove(key);
        }
    }

    /**
     * Evicts a route entry by its matchable path and tenant ID.
     *
     * @param path matchable route path
     * @param tenantId tenant ID
     */
    public void evict(String path, UUID tenantId) {
        RouteKey key = new RouteKey(path, tenantId);
        RouteEntry removed = routes.remove(key);
        if (removed != null && removed.routeId() != null) {
            routeIdIndex.remove(removed.routeId());
        }
    }

    /**
     * Looks up a route by path and tenant ID.
     * Evaluates exact path match first, then falls back to the longest matching path prefix.
     *
     * @param path request path
     * @param tenantId optional tenant ID
     * @return matching RouteEntry, or empty Optional
     */
    public Optional<RouteEntry> lookup(String path, UUID tenantId) {
        if (path == null) {
            return Optional.empty();
        }
        String normalized = RouteKey.normalize(path);

        if (tenantId != null) {
            RouteKey exactKey = new RouteKey(normalized, tenantId);
            RouteEntry exact = routes.get(exactKey);
            if (exact != null) {
                return Optional.of(exact);
            }

            return routes.values().stream()
                    .filter(e -> Objects.equals(e.tenantId(), tenantId))
                    .filter(e -> matchesPath(normalized, RouteKey.normalize(e.path())))
                    .max(Comparator.comparingInt(e -> RouteKey.normalize(e.path()).length()));
        }

        return lookup(path);
    }

    /**
     * Looks up a route by path and tenant ID string.
     *
     * @param path request path
     * @param rawTenantId optional tenant ID string (UUID format)
     * @return matching RouteEntry, or empty Optional
     */
    public Optional<RouteEntry> lookup(String path, String rawTenantId) {
        if (rawTenantId == null || rawTenantId.isBlank()) {
            return lookup(path);
        }
        try {
            return lookup(path, UUID.fromString(rawTenantId.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Looks up a route by request path across all tenants.
     *
     * @param path request path
     * @return matching RouteEntry, or empty Optional
     */
    public Optional<RouteEntry> lookup(String path) {
        if (path == null) {
            return Optional.empty();
        }
        String normalized = RouteKey.normalize(path);

        for (RouteEntry entry : routes.values()) {
            if (RouteKey.normalize(entry.path()).equals(normalized)) {
                return Optional.of(entry);
            }
        }

        return routes.values().stream()
                .filter(e -> matchesPath(normalized, RouteKey.normalize(e.path())))
                .max(Comparator.comparingInt(e -> RouteKey.normalize(e.path()).length()));
    }

    public Optional<RouteEntry> get(String path, UUID tenantId) {
        return lookup(path, tenantId);
    }

    public Optional<RouteEntry> get(String path, String rawTenantId) {
        return lookup(path, rawTenantId);
    }

    public Optional<RouteEntry> get(String path) {
        return lookup(path);
    }

    public int size() {
        return routes.size();
    }

    public boolean isEmpty() {
        return routes.isEmpty();
    }

    public void clear() {
        routes.clear();
        routeIdIndex.clear();
    }

    public List<RouteEntry> getAll() {
        return List.copyOf(routes.values());
    }

    private boolean matchesPath(String requestPath, String routePath) {
        if (requestPath.equals(routePath)) {
            return true;
        }
        if ("/".equals(routePath)) {
            return true;
        }
        return requestPath.startsWith(routePath + "/");
    }
}
