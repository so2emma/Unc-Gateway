package com.unc.gateway.core.reconciliation;

import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 60-second periodic full-table reconciliation poll that queries every row
 * of the services and routes tables via {@link RouteCacheLoader#loadAllRoutes()}
 * and unconditionally bulk-replaces into {@link RouteCache}.
 * <p>
 * Acts as a safety net for missed or dropped LISTEN/NOTIFY events.
 */
@Component
public class ReconciliationPoller {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationPoller.class);

    private final RouteCacheLoader routeCacheLoader;
    private final RouteCache routeCache;
    private final long intervalMs;

    private volatile Instant lastReconciliationTime;
    private volatile long reconciliationCount = 0;

    public ReconciliationPoller(RouteCacheLoader routeCacheLoader, RouteCache routeCache) {
        this(routeCacheLoader, routeCache, 60000L);
    }

    @Autowired
    public ReconciliationPoller(
            RouteCacheLoader routeCacheLoader,
            RouteCache routeCache,
            @Value("${gateway.reconciliation.interval-ms:60000}") long intervalMs
    ) {
        this.routeCacheLoader = Objects.requireNonNull(routeCacheLoader, "routeCacheLoader must not be null");
        this.routeCache = Objects.requireNonNull(routeCache, "routeCache must not be null");
        this.intervalMs = intervalMs;
    }

    /**
     * Periodic reconciliation poll tick scheduled at fixed rate (default 60s).
     */
    @Scheduled(fixedRateString = "${gateway.reconciliation.interval-ms:60000}")
    public void poll() {
        reconcile();
    }

    /**
     * Executes a full-table reconciliation pass: queries all routes joined with services
     * from PostgreSQL and unconditionally bulk-replaces the in-memory RouteCache.
     */
    public void reconcile() {
        try {
            log.debug("Starting periodic route cache reconciliation poll...");
            List<RouteEntry> routes = routeCacheLoader.loadAllRoutes().block(Duration.ofSeconds(10));
            if (routes != null) {
                routeCache.bulkReplace(routes);
                this.lastReconciliationTime = Instant.now();
                this.reconciliationCount++;
                log.info("Reconciliation poll completed successfully: unconditionally replaced RouteCache with {} routes (run #{})",
                        routes.size(), reconciliationCount);
            }
        } catch (Exception e) {
            log.warn("Reconciliation poll failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Alias for {@link #reconcile()} / {@link #poll()}.
     */
    public void run() {
        reconcile();
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public Instant getLastReconciliationTime() {
        return lastReconciliationTime;
    }

    public long getReconciliationCount() {
        return reconciliationCount;
    }
}
