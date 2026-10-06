package com.unc.gateway.core.cache;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loads joined {@code services} and {@code routes} from PostgreSQL via R2DBC at application startup
 * and populates {@link RouteCache} via bulk-replace.
 */
@Component
public class RouteCacheLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RouteCacheLoader.class);

    static final String SELECT_ALL_ROUTES_SQL = """
            SELECT
                r.id AS route_id,
                r.service_id AS service_id,
                COALESCE(r.tenant_id, s.tenant_id) AS tenant_id,
                r.paths AS paths,
                COALESCE(r.strip_path, TRUE) AS strip_path,
                s.url AS upstream_url,
                COALESCE(s.tls_enabled, FALSE) AS tls_enabled,
                COALESCE(s.mtls_enabled, FALSE) AS mtls_enabled
            FROM routes r
            JOIN services s ON r.service_id = s.id
            """;

    static final String SELECT_ROUTE_BY_ID_SQL = """
            SELECT
                r.id AS route_id,
                r.service_id AS service_id,
                COALESCE(r.tenant_id, s.tenant_id) AS tenant_id,
                r.paths AS paths,
                COALESCE(r.strip_path, TRUE) AS strip_path,
                s.url AS upstream_url,
                COALESCE(s.tls_enabled, FALSE) AS tls_enabled,
                COALESCE(s.mtls_enabled, FALSE) AS mtls_enabled
            FROM routes r
            JOIN services s ON r.service_id = s.id
            WHERE r.id = :routeId
            """;

    static final String SELECT_ROUTES_BY_SERVICE_ID_SQL = """
            SELECT
                r.id AS route_id,
                r.service_id AS service_id,
                COALESCE(r.tenant_id, s.tenant_id) AS tenant_id,
                r.paths AS paths,
                COALESCE(r.strip_path, TRUE) AS strip_path,
                s.url AS upstream_url,
                COALESCE(s.tls_enabled, FALSE) AS tls_enabled,
                COALESCE(s.mtls_enabled, FALSE) AS mtls_enabled
            FROM routes r
            JOIN services s ON r.service_id = s.id
            WHERE r.service_id = :serviceId
            """;

    private final DatabaseClient databaseClient;
    private final RouteCache routeCache;

    public RouteCacheLoader(DatabaseClient databaseClient, RouteCache routeCache) {
        this.databaseClient = databaseClient;
        this.routeCache = routeCache;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            loadAndPopulateCache().block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("Could not populate RouteCache from database on startup: {}", e.getMessage());
        }
    }

    /**
     * Queries all routes joined with services from PostgreSQL and returns the expanded RouteEntry list.
     */
    public Mono<List<RouteEntry>> loadAllRoutes() {
        return databaseClient.sql(SELECT_ALL_ROUTES_SQL)
                .map(this::mapRow)
                .all()
                .flatMap(this::expandPaths)
                .collectList();
    }

    /**
     * Queries PostgreSQL and bulk-replaces the entries in {@link RouteCache}.
     */
    public Mono<List<RouteEntry>> loadAndPopulateCache() {
        return loadAllRoutes()
                .doOnNext(routeCache::bulkReplace)
                .doOnSuccess(routes -> log.info("Successfully loaded {} routes into RouteCache", routes.size()))
                .doOnError(err -> log.warn("Failed to load routes from database: {}", err.getMessage()));
    }

    /**
     * Single-row reload by route ID (for targeted cache invalidation).
     */
    public Mono<RouteEntry> loadRouteById(UUID routeId) {
        return databaseClient.sql(SELECT_ROUTE_BY_ID_SQL)
                .bind("routeId", routeId)
                .map(this::mapRow)
                .one();
    }

    /**
     * Reloads all expanded route entries for a given route ID.
     */
    public Mono<List<RouteEntry>> loadRoutesByRouteId(UUID routeId) {
        return databaseClient.sql(SELECT_ROUTE_BY_ID_SQL)
                .bind("routeId", routeId)
                .map(this::mapRow)
                .all()
                .flatMap(this::expandPaths)
                .collectList();
    }

    /**
     * Reloads all expanded route entries attached to a given service ID.
     */
    public Mono<List<RouteEntry>> loadRoutesByServiceId(UUID serviceId) {
        return databaseClient.sql(SELECT_ROUTES_BY_SERVICE_ID_SQL)
                .bind("serviceId", serviceId)
                .map(this::mapRow)
                .all()
                .flatMap(this::expandPaths)
                .collectList();
    }

    RouteEntry mapRow(Row row, RowMetadata metadata) {
        UUID routeId = row.get("route_id", UUID.class);
        UUID serviceId = row.get("service_id", UUID.class);
        UUID tenantId = row.get("tenant_id", UUID.class);
        String paths = row.get("paths", String.class);
        Boolean stripPath = row.get("strip_path", Boolean.class);
        String upstreamUrl = row.get("upstream_url", String.class);
        Boolean tlsEnabled = row.get("tls_enabled", Boolean.class);
        Boolean mtlsEnabled = row.get("mtls_enabled", Boolean.class);

        return new RouteEntry(
                routeId,
                serviceId,
                tenantId,
                paths != null ? paths : "/",
                upstreamUrl != null ? upstreamUrl : "",
                stripPath == null || stripPath,
                Boolean.TRUE.equals(tlsEnabled),
                Boolean.TRUE.equals(mtlsEnabled)
        );
    }

    private Flux<RouteEntry> expandPaths(RouteEntry entry) {
        String p = entry.path();
        if (p == null || !p.contains(",")) {
            return Flux.just(entry);
        }
        String[] parts = p.split(",");
        List<RouteEntry> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(new RouteEntry(
                        entry.routeId(),
                        entry.serviceId(),
                        entry.tenantId(),
                        trimmed,
                        entry.upstreamUrl(),
                        entry.stripPath(),
                        entry.tlsEnabled(),
                        entry.mtlsEnabled()
                ));
            }
        }
        return Flux.fromIterable(list);
    }
}
