package com.unc.admin.api.repository;

import com.unc.admin.api.entity.ConsumerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the {@code consumers} table. Every derived query carries an explicit
 * {@code tenant_id} predicate, as mandated by the tenant isolation rule.
 */
@Repository
public interface ConsumerRepository extends JpaRepository<ConsumerEntity, UUID> {

    List<ConsumerEntity> findByTenantId(UUID tenantId);

    Optional<ConsumerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ConsumerEntity> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
