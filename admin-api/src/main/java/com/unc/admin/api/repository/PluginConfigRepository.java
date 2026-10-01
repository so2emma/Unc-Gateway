package com.unc.admin.api.repository;

import com.unc.admin.api.entity.PluginConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the {@code plugin_configs} table. Every derived query carries an explicit
 * {@code tenant_id} predicate, as mandated by the tenant isolation rule.
 */
@Repository
public interface PluginConfigRepository extends JpaRepository<PluginConfigEntity, UUID> {

    List<PluginConfigEntity> findByTenantId(UUID tenantId);

    Optional<PluginConfigEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PluginConfigEntity> findByServiceIdAndTenantId(UUID serviceId, UUID tenantId);

    List<PluginConfigEntity> findByRouteIdAndTenantId(UUID routeId, UUID tenantId);

    List<PluginConfigEntity> findByConsumerIdAndTenantId(UUID consumerId, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
