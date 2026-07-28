package com.unc.admin.api.repository;

import com.unc.admin.api.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {

    List<RouteEntity> findByTenantId(UUID tenantId);

    Optional<RouteEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    List<RouteEntity> findByServiceIdAndTenantId(UUID serviceId, UUID tenantId);
}
