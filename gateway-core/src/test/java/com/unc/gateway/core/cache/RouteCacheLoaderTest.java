package com.unc.gateway.core.cache;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class RouteCacheLoaderTest {

    @Test
    @DisplayName("using a stubbed R2DBC client, verifies each returned services/routes row is correctly mapped into a RouteEntry and passed to RouteCache's bulk-replace operation")
    @SuppressWarnings("unchecked")
    void testRouteCacheLoaderMapsRowsAndCallsBulkReplace() {
        DatabaseClient databaseClient = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<RouteEntry> fetchSpec = mock(RowsFetchSpec.class);
        Row row = mock(Row.class);
        RowMetadata metadata = mock(RowMetadata.class);
        RouteCache routeCache = mock(RouteCache.class);

        UUID routeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        given(row.get("route_id", UUID.class)).willReturn(routeId);
        given(row.get("service_id", UUID.class)).willReturn(serviceId);
        given(row.get("tenant_id", UUID.class)).willReturn(tenantId);
        given(row.get("paths", String.class)).willReturn("/demo");
        given(row.get("strip_path", Boolean.class)).willReturn(true);
        given(row.get("upstream_url", String.class)).willReturn("http://mock-upstream:8080");

        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.map(any(BiFunction.class))).willAnswer(invocation -> {
            BiFunction<Row, RowMetadata, RouteEntry> mapper = invocation.getArgument(0);
            RouteEntry mapped = mapper.apply(row, metadata);
            given(fetchSpec.all()).willReturn(Flux.just(mapped));
            return fetchSpec;
        });

        RouteCacheLoader loader = new RouteCacheLoader(databaseClient, routeCache);
        List<RouteEntry> loaded = loader.loadAndPopulateCache().block();

        assertThat(loaded).isNotNull().hasSize(1);
        RouteEntry entry = loaded.get(0);
        assertThat(entry.routeId()).isEqualTo(routeId);
        assertThat(entry.serviceId()).isEqualTo(serviceId);
        assertThat(entry.tenantId()).isEqualTo(tenantId);
        assertThat(entry.path()).isEqualTo("/demo");
        assertThat(entry.upstreamUrl()).isEqualTo("http://mock-upstream:8080");
        assertThat(entry.stripPath()).isTrue();

        ArgumentCaptor<Collection<RouteEntry>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(routeCache, times(1)).bulkReplace(captor.capture());
        assertThat(captor.getValue()).containsExactly(entry);
    }

    @Test
    @DisplayName("maps comma-separated paths into multiple distinct RouteEntry instances")
    @SuppressWarnings("unchecked")
    void testCommaSeparatedPathsExpansion() {
        DatabaseClient databaseClient = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<RouteEntry> fetchSpec = mock(RowsFetchSpec.class);
        Row row = mock(Row.class);
        RowMetadata metadata = mock(RowMetadata.class);
        RouteCache routeCache = mock(RouteCache.class);

        UUID routeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        given(row.get("route_id", UUID.class)).willReturn(routeId);
        given(row.get("service_id", UUID.class)).willReturn(serviceId);
        given(row.get("tenant_id", UUID.class)).willReturn(tenantId);
        given(row.get("paths", String.class)).willReturn("/v1, /v2");
        given(row.get("strip_path", Boolean.class)).willReturn(false);
        given(row.get("upstream_url", String.class)).willReturn("http://upstream:8080");

        given(databaseClient.sql(anyString())).willReturn(executeSpec);
        given(executeSpec.map(any(BiFunction.class))).willAnswer(invocation -> {
            BiFunction<Row, RowMetadata, RouteEntry> mapper = invocation.getArgument(0);
            RouteEntry mapped = mapper.apply(row, metadata);
            given(fetchSpec.all()).willReturn(Flux.just(mapped));
            return fetchSpec;
        });

        RouteCacheLoader loader = new RouteCacheLoader(databaseClient, routeCache);
        List<RouteEntry> loaded = loader.loadAllRoutes().block();

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).path()).isEqualTo("/v1");
        assertThat(loaded.get(1).path()).isEqualTo("/v2");
    }
}
