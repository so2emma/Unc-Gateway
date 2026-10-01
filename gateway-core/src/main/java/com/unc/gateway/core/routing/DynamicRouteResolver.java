package com.unc.gateway.core.routing;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves inbound requests against the in-memory {@link RouteCache}, determining
 * the upstream target URL or returning 404 Not Found when no route matches.
 */
@Component
public class DynamicRouteResolver {

    private final RouteCache routeCache;

    public DynamicRouteResolver(RouteCache routeCache) {
        this.routeCache = routeCache;
    }

    public Optional<RouteEntry> findRoute(String path, UUID tenantId) {
        return routeCache.lookup(path, tenantId);
    }

    public Optional<RouteEntry> findRoute(String path, String tenantId) {
        return routeCache.lookup(path, tenantId);
    }

    public Optional<RouteEntry> findRoute(String path) {
        return routeCache.lookup(path);
    }

    public Optional<RouteEntry> findRoute(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        UUID tenantId = extractTenantId(exchange);
        return routeCache.lookup(path, tenantId);
    }

    public RouteEntry resolveRoute(String path, UUID tenantId) {
        return findRoute(path, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No route found for path: " + path));
    }

    public RouteEntry resolveRoute(String path) {
        return findRoute(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No route found for path: " + path));
    }

    public String resolve(String path, UUID tenantId) {
        return resolveRoute(path, tenantId).upstreamUrl();
    }

    public String resolve(String path) {
        return resolveRoute(path).upstreamUrl();
    }

    public String resolveTargetUrl(String path, UUID tenantId) {
        RouteEntry route = resolveRoute(path, tenantId);
        return buildTargetUrl(route, path, null);
    }

    public String resolveTargetUrl(String path) {
        RouteEntry route = resolveRoute(path);
        return buildTargetUrl(route, path, null);
    }

    public Mono<String> resolveTarget(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        UUID tenantId = extractTenantId(exchange);

        return Mono.justOrEmpty(routeCache.lookup(path, tenantId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No route found for path: " + path)))
                .map(route -> buildTargetUrl(route, path, request.getURI().getRawQuery()));
    }

    public static String buildTargetUrl(RouteEntry route, String requestPath, String query) {
        String baseUrl = route.upstreamUrl().replaceAll("/+$", "");
        String subPath;
        if (route.stripPath()) {
            String routePath = route.path().replaceAll("/+$", "");
            if (requestPath.startsWith(routePath)) {
                subPath = requestPath.substring(routePath.length());
            } else {
                subPath = requestPath;
            }
            if (!subPath.isEmpty() && !subPath.startsWith("/")) {
                subPath = "/" + subPath;
            }
        } else {
            subPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
        }

        String target = baseUrl + subPath;
        if (target.isEmpty()) {
            target = "/";
        }
        if (query != null && !query.isEmpty()) {
            target += "?" + query;
        }
        return target;
    }

    private UUID extractTenantId(ServerWebExchange exchange) {
        String rawTenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (rawTenantId == null || rawTenantId.isBlank()) {
            rawTenantId = exchange.getRequest().getQueryParams().getFirst("tenant_id");
        }
        if (rawTenantId != null && !rawTenantId.isBlank()) {
            try {
                return UUID.fromString(rawTenantId.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
