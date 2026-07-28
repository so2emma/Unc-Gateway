package com.unc.admin.api.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext - set, get, and clear tenant_id in ThreadLocal context")
    void testTenantContextLifecycle() {
        assertThat(TenantContext.getTenantId()).isNull();

        TenantContext.setTenantId("tenant-alpha");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-alpha");

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
