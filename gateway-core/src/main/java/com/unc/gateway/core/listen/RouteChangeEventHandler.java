package com.unc.gateway.core.listen;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Consumes each {@link RouteChangeEvent} emitted by {@link RouteChangeListener},
 * re-reads affected rows from PostgreSQL via {@link RouteCacheLoader}'s single-row path
 * (or evicts them on {@code DELETE}), and updates {@link RouteCache} in place without
 * requiring a full cache reload or gateway restart.
 */
@Component
public class RouteChangeEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RouteChangeEventHandler.class);

    private final RouteCacheLoader routeCacheLoader;
    private final RouteCache routeCache;
    private final com.unc.gateway.plugins.PluginConfigLoader pluginConfigLoader;

    public RouteChangeEventHandler(RouteCacheLoader routeCacheLoader, RouteCache routeCache) {
        this(routeCacheLoader, routeCache, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RouteChangeEventHandler(RouteCacheLoader routeCacheLoader, RouteCache routeCache,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false) com.unc.gateway.plugins.PluginConfigLoader pluginConfigLoader) {
        this.routeCacheLoader = Objects.requireNonNull(routeCacheLoader, "routeCacheLoader must not be null");
        this.routeCache = Objects.requireNonNull(routeCache, "routeCache must not be null");
        this.pluginConfigLoader = pluginConfigLoader;
    }

    /**
     * Handles an incoming invalidation event.
     *
     * @param event the parsed route change event
     * @return completion signal
     */
    public Mono<Void> handleEvent(RouteChangeEvent event) {
        if (event == null) {
            return Mono.empty();
        }

        log.info("Handling invalidation event: operation={}, table={}, id={}, tenantId={}",
                event.operation(), event.table(), event.id(), event.tenantId());

        if (event.isRoute()) {
            return handleRouteEvent(event);
        } else if (event.isService()) {
            return handleServiceEvent(event);
        } else if (event.isPluginConfig()) {
            return handlePluginConfigEvent(event);
        } else {
            log.warn("Unrecognized table in change event: {}", event.table());
            return Mono.empty();
        }
    }

    private Mono<Void> handleRouteEvent(RouteChangeEvent event) {
        if (event.isDelete()) {
            log.info("Evicting route from RouteCache: id={}", event.id());
            routeCache.evict(event.id());
            return Mono.empty();
        }

        // INSERT or UPDATE: re-read affected row via RouteCacheLoader single-row path
        log.info("Reloading route from database: id={}", event.id());
        return routeCacheLoader.loadRouteById(event.id())
                .doOnNext(entry -> {
                    // Evict existing entry in case path changed, then put the fresh entry
                    routeCache.evict(event.id());
                    if (entry.path() != null && entry.path().contains(",")) {
                        for (String part : entry.path().split(",")) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                routeCache.put(new RouteEntry(
                                        entry.routeId(),
                                        entry.serviceId(),
                                        entry.tenantId(),
                                        trimmed,
                                        entry.upstreamUrl(),
                                        entry.stripPath()
                                ));
                            }
                        }
                    } else {
                        routeCache.put(entry);
                    }
                    log.info("Successfully updated RouteCache for route id={}: path={}, upstream={}",
                            entry.routeId(), entry.path(), entry.upstreamUrl());
                })
                .switchIfEmpty(Mono.fromRunnable(() -> {
                    log.warn("Route id={} not found in database; evicting from RouteCache", event.id());
                    routeCache.evict(event.id());
                }))
                .then();
    }

    private Mono<Void> handleServiceEvent(RouteChangeEvent event) {
        if (event.isDelete()) {
            log.info("Evicting routes for deleted service id={}", event.id());
            routeCache.evictByServiceId(event.id());
            return Mono.empty();
        }

        if (event.isInsert()) {
            log.info("New service created id={}; awaiting routes creation", event.id());
            return Mono.empty();
        }

        // UPDATE: reload all routes that point to this service so their upstream targets are fresh
        log.info("Reloading routes for updated service id={}", event.id());
        return routeCacheLoader.loadRoutesByServiceId(event.id())
                .doOnNext(entries -> {
                    routeCache.evictByServiceId(event.id());
                    for (RouteEntry entry : entries) {
                        routeCache.put(entry);
                    }
                    log.info("Successfully reloaded {} routes for service id={}", entries.size(), event.id());
                })
                .then();
    }

    private Mono<Void> handlePluginConfigEvent(RouteChangeEvent event) {
        log.info("Received plugin_configs event: op={}, id={}, tenantId={}",
                event.operation(), event.id(), event.tenantId());
        if (pluginConfigLoader != null) {
            return pluginConfigLoader.loadAndPopulateCache().then();
        }
        return Mono.empty();
    }
}
