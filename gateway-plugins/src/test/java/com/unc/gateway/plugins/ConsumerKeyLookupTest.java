package com.unc.gateway.plugins;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ConsumerKeyLookupTest {

    @Test
    @DisplayName("sha256Hex: computes consistent SHA-256 hex string")
    void testSha256Hex() {
        String hash = ConsumerKeyLookup.sha256Hex("unc_key_test123");
        assertThat(hash).isNotNull().hasSize(64);
        assertThat(ConsumerKeyLookup.sha256Hex("unc_key_test123")).isEqualTo(hash);
    }

    @Test
    @DisplayName("ConsumerKeyLookup: never exposes or compares raw key; queries only by SHA-256 hash")
    void testNeverExposesRawKey() {
        DatabaseClient client = Mockito.mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<ConsumerIdentity> fetchSpec = Mockito.mock(FetchSpec.class);

        AtomicReference<String> boundKeyHash = new AtomicReference<>();
        when(client.sql(anyString())).thenReturn(spec);
        when(spec.bind(Mockito.eq("keyHash"), Mockito.any(String.class))).thenAnswer(invocation -> {
            boundKeyHash.set(invocation.getArgument(1));
            return spec;
        });

        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        ConsumerIdentity expected = new ConsumerIdentity(tenantId, consumerId, UUID.randomUUID(), "default", "acme");

        when(spec.map(any(BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.just(expected));

        ConsumerKeyLookup lookup = new ConsumerKeyLookup(client);
        String rawKey = "unc_key_secret_raw_material";
        String expectedHash = ConsumerKeyLookup.sha256Hex(rawKey);

        StepVerifier.create(lookup.lookup(rawKey))
                .assertNext(identity -> {
                    assertThat(identity.tenantId()).isEqualTo(tenantId);
                    assertThat(identity.consumerId()).isEqualTo(consumerId);
                })
                .verifyComplete();

        // Verify the database query was bound to the hash, NOT the raw key
        assertThat(boundKeyHash.get()).isEqualTo(expectedHash);
        assertThat(boundKeyHash.get()).isNotEqualTo(rawKey);
    }

    @Test
    @DisplayName("ConsumerKeyLookup: key belonging to another tenant resolves to owning tenant rather than caller's")
    void testKeyResolvesToOwningTenantRegardlessOfContext() {
        DatabaseClient client = Mockito.mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<ConsumerIdentity> fetchSpec = Mockito.mock(FetchSpec.class);

        when(client.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);

        UUID trueOwningTenantId = UUID.randomUUID();
        UUID callerContextTenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();
        ConsumerIdentity owningIdentity = new ConsumerIdentity(trueOwningTenantId, consumerId, UUID.randomUUID(), "key", "owner");

        when(spec.map(any(BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.just(owningIdentity));

        ConsumerKeyLookup lookup = new ConsumerKeyLookup(client);

        StepVerifier.create(lookup.lookup("raw-key-value", callerContextTenantId))
                .assertNext(identity -> {
                    assertThat(identity.tenantId()).isEqualTo(trueOwningTenantId);
                    assertThat(identity.tenantId()).isNotEqualTo(callerContextTenantId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ConsumerKeyLookup: mapRow maps columns properly")
    void testMapRow() {
        Row row = Mockito.mock(Row.class);
        RowMetadata meta = Mockito.mock(RowMetadata.class);

        UUID keyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID consumerId = UUID.randomUUID();

        when(row.get("key_id", UUID.class)).thenReturn(keyId);
        when(row.get("tenant_id", UUID.class)).thenReturn(tenantId);
        when(row.get("consumer_id", UUID.class)).thenReturn(consumerId);
        when(row.get("key_name", String.class)).thenReturn("key-1");
        when(row.get("username", String.class)).thenReturn("acme");

        ConsumerKeyLookup lookup = new ConsumerKeyLookup(Mockito.mock(DatabaseClient.class));
        ConsumerIdentity identity = lookup.mapRow(row, meta);

        assertThat(identity.keyId()).isEqualTo(keyId);
        assertThat(identity.tenantId()).isEqualTo(tenantId);
        assertThat(identity.consumerId()).isEqualTo(consumerId);
        assertThat(identity.keyName()).isEqualTo("key-1");
        assertThat(identity.username()).isEqualTo("acme");
    }
}
