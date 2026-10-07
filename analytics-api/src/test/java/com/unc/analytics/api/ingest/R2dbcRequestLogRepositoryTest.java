package com.unc.analytics.api.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;

class R2dbcRequestLogRepositoryTest {

    private final DatabaseClient databaseClient = mock(DatabaseClient.class);
    private final R2dbcRequestLogRepository repository = new R2dbcRequestLogRepository(databaseClient);

    @Test
    @DisplayName("save with null tenantId rejects with IllegalArgumentException enforcing isolation rule")
    void testSaveWithNullTenantIdRejects() {
        IngestRequest request = new IngestRequest();
        request.setMethod("GET");
        request.setPath("/test");

        StepVerifier.create(repository.save(null, request))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }

    @Test
    @DisplayName("save RequestLog record with null tenantId rejects with IllegalArgumentException")
    void testSaveRecordWithNullTenantIdRejects() {
        RequestLog record = new RequestLog();
        record.setId(UUID.randomUUID());

        StepVerifier.create(repository.save(record))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }

    @Test
    @DisplayName("findByTenantId with null tenantId rejects with IllegalArgumentException")
    void testFindByTenantIdWithNullRejects() {
        StepVerifier.create(repository.findByTenantId(null))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }

    @Test
    @DisplayName("findByIdAndTenantId with null tenantId rejects with IllegalArgumentException")
    void testFindByIdAndTenantIdWithNullRejects() {
        StepVerifier.create(repository.findByIdAndTenantId(UUID.randomUUID(), null))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException)
                .verify();
    }

    @Test
    @DisplayName("countByTenantId with null tenantId rejects with IllegalArgumentException")
    void testCountByTenantIdWithNullRejects() {
        StepVerifier.create(repository.countByTenantId(null))
                .expectErrorMatches(err -> err instanceof IllegalArgumentException
                        && err.getMessage().contains("tenant_id must not be null"))
                .verify();
    }
}
