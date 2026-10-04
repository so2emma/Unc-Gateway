package com.unc.gateway.core.reconciliation;

import com.unc.gateway.core.GatewayCoreApplication;
import com.unc.gateway.core.cache.RouteCache;
import com.unc.gateway.core.cache.RouteCacheLoader;
import com.unc.gateway.core.cache.RouteEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@DisplayName("Phase 15: ReconciliationPoller Unit Tests")
class ReconciliationPollerTest {

    private RouteCacheLoader routeCacheLoader;
    private RouteCache mockRouteCache;
    private ReconciliationPoller poller;

    @BeforeEach
    void setUp() {
        routeCacheLoader = mock(RouteCacheLoader.class);
        mockRouteCache = mock(RouteCache.class);
        poller = new ReconciliationPoller(routeCacheLoader, mockRouteCache);
    }

    @Test
    @DisplayName("single invocation queries every row from RouteCacheLoader and passes the full result set to RouteCache's bulk-replace")
    void testSingleInvocationQueriesEveryRowAndBulkReplaces() {
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<RouteEntry> fixedEntries = List.of(
                new RouteEntry(UUID.randomUUID(), serviceId, tenantId, "/demo", "http://upstream:8080", true),
                new RouteEntry(UUID.randomUUID(), serviceId, tenantId, "/orders", "http://upstream:8080", false)
        );

        when(routeCacheLoader.loadAllRoutes()).thenReturn(Mono.just(fixedEntries));

        poller.poll();

        verify(routeCacheLoader, times(1)).loadAllRoutes();
        ArgumentCaptor<Collection<RouteEntry>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mockRouteCache, times(1)).bulkReplace(captor.capture());

        assertThat(captor.getValue()).containsExactlyElementsOf(fixedEntries);
        assertThat(poller.getReconciliationCount()).isEqualTo(1);
        assertThat(poller.getLastReconciliationTime()).isNotNull();
    }

    @Test
    @DisplayName("reconcile() method queries every row and bulk-replaces RouteCache")
    void testReconcileMethodDirectInvocation() {
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<RouteEntry> fixedEntries = List.of(
                new RouteEntry(UUID.randomUUID(), serviceId, tenantId, "/v1", "http://upstream:8080", true)
        );

        when(routeCacheLoader.loadAllRoutes()).thenReturn(Mono.just(fixedEntries));

        poller.reconcile();

        verify(routeCacheLoader, times(1)).loadAllRoutes();
        verify(mockRouteCache, times(1)).bulkReplace(fixedEntries);
        assertThat(poller.getReconciliationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("unconditionally performs a full bulk-replace rather than a diff against prior cache state")
    void testUnconditionalBulkReplaceRegardlessOfCacheContents() {
        // Use a real in-memory RouteCache instance to verify unconditional replacement
        RouteCache realCache = new RouteCache();
        UUID tenantId = UUID.randomUUID();
        UUID oldRouteId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        // Populate stale entry in RouteCache
        realCache.put(new RouteEntry(oldRouteId, serviceId, tenantId, "/stale-path", "http://old-upstream:8080", true));
        assertThat(realCache.lookup("/stale-path", tenantId)).isPresent();
        assertThat(realCache.size()).isEqualTo(1);

        // Stub loader to return completely different entries
        UUID newRouteId = UUID.randomUUID();
        List<RouteEntry> newEntries = List.of(
                new RouteEntry(newRouteId, serviceId, tenantId, "/new-path", "http://new-upstream:8080", true)
        );
        when(routeCacheLoader.loadAllRoutes()).thenReturn(Mono.just(newEntries));

        ReconciliationPoller realPoller = new ReconciliationPoller(routeCacheLoader, realCache);
        realPoller.poll();

        // The stale entry must be gone; the new entry must be present
        assertThat(realCache.lookup("/stale-path", tenantId)).isEmpty();
        assertThat(realCache.lookup("/new-path", tenantId)).isPresent();
        assertThat(realCache.size()).isEqualTo(1);
        assertThat(realCache.lookup("/new-path", tenantId).get().routeId()).isEqualTo(newRouteId);
    }

    @Test
    @DisplayName("verifies component is scheduled at fixed 60000ms rate via @Scheduled or interval-ms property")
    void testScheduledAnnotationRate() throws NoSuchMethodException {
        // Find @Scheduled annotation on ReconciliationPoller
        Method scheduledMethod = Arrays.stream(ReconciliationPoller.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .findFirst()
                .orElse(null);

        assertThat(scheduledMethod).isNotNull();
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();

        boolean hasSixtySecondRate = scheduled.fixedRate() == 60000L ||
                scheduled.fixedRateString().contains("60000") ||
                scheduled.fixedRateString().contains("gateway.reconciliation.interval-ms");

        assertThat(hasSixtySecondRate)
                .as("Scheduled rate must be 60000ms or reference gateway.reconciliation.interval-ms")
                .isTrue();

        assertThat(poller.getIntervalMs()).isEqualTo(60000L);
    }

    @Test
    @DisplayName("GatewayCoreApplication is annotated with @EnableScheduling")
    void testGatewayCoreApplicationHasEnableScheduling() {
        assertThat(GatewayCoreApplication.class.isAnnotationPresent(EnableScheduling.class))
                .as("GatewayCoreApplication must have @EnableScheduling present")
                .isTrue();
    }

    @Test
    @DisplayName("handles RouteCacheLoader errors gracefully without rethrowing or crashing")
    void testHandlesErrorsGracefully() {
        when(routeCacheLoader.loadAllRoutes()).thenReturn(Mono.error(new RuntimeException("Connection refused")));

        assertThatCode(() -> poller.poll()).doesNotThrowAnyException();
        verify(mockRouteCache, never()).bulkReplace(any());
        assertThat(poller.getReconciliationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("handles empty Mono from RouteCacheLoader gracefully")
    void testHandlesEmptyMonoGracefully() {
        when(routeCacheLoader.loadAllRoutes()).thenReturn(Mono.empty());

        assertThatCode(() -> poller.poll()).doesNotThrowAnyException();
        verify(mockRouteCache, never()).bulkReplace(any());
    }
}
