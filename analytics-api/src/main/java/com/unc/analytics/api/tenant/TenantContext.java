package com.unc.analytics.api.tenant;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Resolves the tenant context for every analytics API request from the {@code X-Tenant-Id} header
 * and enforces tenant isolation at the gateway entry boundary before requests reach the repository layer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContext implements WebFilter {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String TENANT_ID_ATTR = "tenant_id";
    public static final String RAW_TENANT_ID_ATTR = "raw_tenant_id";
    public static final String TENANT_ID_KEY = "tenant_id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Enforce tenant presence on all analytics API endpoints
        if (path.startsWith("/api/analytics")) {
            String header = exchange.getRequest().getHeaders().getFirst(TENANT_ID_HEADER);
            if (header == null || header.trim().isEmpty()) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required"));
            }

            String trimmed = header.trim();
            UUID tenantId = parseTenantId(trimmed);
            if (tenantId == null) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-Tenant-Id header"));
            }

            exchange.getAttributes().put(TENANT_ID_ATTR, tenantId);
            exchange.getAttributes().put(RAW_TENANT_ID_ATTR, trimmed);

            return chain.filter(exchange)
                    .contextWrite(Context.of(TENANT_ID_KEY, tenantId));
        }

        return chain.filter(exchange);
    }

    /**
     * Resolves the current request's tenant UUID from exchange attributes or the {@code X-Tenant-Id} header.
     */
    public static UUID getTenantId(ServerWebExchange exchange) {
        if (exchange == null) {
            return null;
        }
        Object attr = exchange.getAttribute(TENANT_ID_ATTR);
        if (attr instanceof UUID uuid) {
            return uuid;
        }
        if (attr instanceof String str) {
            return parseTenantId(str);
        }
        String header = exchange.getRequest().getHeaders().getFirst(TENANT_ID_HEADER);
        return parseTenantId(header);
    }

    /**
     * Resolves the current request's tenant UUID, rejecting missing tenant headers with HTTP 400.
     */
    public static UUID resolveTenantId(ServerWebExchange exchange) {
        UUID tenantId = getTenantId(exchange);
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id header is required");
        }
        return tenantId;
    }

    /**
     * Resolves tenant UUID from the reactive Reactor Context.
     */
    public static Mono<UUID> getTenantId() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(TENANT_ID_KEY)) {
                return Mono.just(ctx.get(TENANT_ID_KEY));
            }
            return Mono.empty();
        });
    }

    /**
     * Parses a raw tenant ID string into a UUID.
     * Supports standard UUID strings as well as deterministic UUIDs for string names (e.g. "tenant-a").
     */
    public static UUID parseTenantId(String rawTenantId) {
        if (rawTenantId == null || rawTenantId.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawTenantId.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
