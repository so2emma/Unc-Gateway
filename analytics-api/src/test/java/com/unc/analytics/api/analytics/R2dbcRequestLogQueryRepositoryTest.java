package com.unc.analytics.api.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class R2dbcRequestLogQueryRepositoryTest {

    private final DatabaseClient databaseClient = mock(DatabaseClient.class);
    private final R2dbcRequestLogQueryRepository repository = new R2dbcRequestLogQueryRepository(databaseClient);

    @Test
    @DisplayName("Latency percentile query construction always includes tenant_id = :tenantId predicate")
    void testLatencyPercentileQueryIncludesTenantIdPredicate() {
        String sql = repository.getLatencyMetricsSql();
        assertThat(sql).isNotBlank();
        assertThat(sql).contains("tenant_id = :tenantId");
        assertThat(sql).contains("percentile_cont(0.95)");
        assertThat(sql).contains("percentile_cont(0.99)");
    }

    @Test
    @DisplayName("Traffic pulse query construction always includes tenant_id = :tenantId predicate")
    void testTrafficPulseQueryIncludesTenantIdPredicate() {
        String sql = repository.getTrafficPulseSql();
        assertThat(sql).isNotBlank();
        assertThat(sql).contains("tenant_id = :tenantId");
        assertThat(sql).contains("date_trunc");
    }

    @Test
    @DisplayName("getLatencyMetrics with null tenantId rejects with IllegalArgumentException enforcing isolation rule")
    void testGetLatencyMetricsNullTenantIdRejects() {
        StepVerifier.create(repository.getLatencyMetrics(null))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }

    @Test
    @DisplayName("getTrafficPulse with null tenantId rejects with IllegalArgumentException enforcing isolation rule")
    void testGetTrafficPulseNullTenantIdRejects() {
        StepVerifier.create(repository.getTrafficPulse(null))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }

    @Test
    @DisplayName("getTrafficPulse with custom limit and null tenantId rejects with IllegalArgumentException")
    void testGetTrafficPulseWithLimitNullTenantIdRejects() {
        StepVerifier.create(repository.getTrafficPulse(null, 10))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }
}
