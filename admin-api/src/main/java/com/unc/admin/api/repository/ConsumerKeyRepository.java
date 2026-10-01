package com.unc.admin.api.repository;

import com.unc.admin.api.entity.ConsumerKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the {@code consumer_keys} table. Every derived query carries an explicit
 * {@code tenant_id} predicate, as mandated by the tenant isolation rule.
 */
@Repository
public interface ConsumerKeyRepository extends JpaRepository<ConsumerKeyEntity, UUID> {

    List<ConsumerKeyEntity> findByConsumerIdAndTenantId(UUID consumerId, UUID tenantId);

    List<ConsumerKeyEntity> findByTenantId(UUID tenantId);

    Optional<ConsumerKeyEntity> findByIdAndConsumerIdAndTenantId(UUID id, UUID consumerId, UUID tenantId);

    Optional<ConsumerKeyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByKeyHash(String keyHash);

    void deleteByIdAndConsumerIdAndTenantId(UUID id, UUID consumerId, UUID tenantId);
}
