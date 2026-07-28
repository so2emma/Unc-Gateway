package com.unc.admin.api.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext - set, get, and clear tenant_id in ThreadLocal context")
    void testTenantContextLifecycle() {
        UUID tenantId = UUID.randomUUID();
        assertThat(TenantContext.getTenantId()).isNull();

        TenantContext.setTenantId(tenantId);
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
