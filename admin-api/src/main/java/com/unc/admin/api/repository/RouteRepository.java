package com.unc.admin.api.repository;

import com.unc.admin.api.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<RouteEntity, String> {

    List<RouteEntity> findByTenantId(String tenantId);

    Optional<RouteEntity> findByIdAndTenantId(String id, String tenantId);

    void deleteByIdAndTenantId(String id, String tenantId);

    List<RouteEntity> findByServiceIdAndTenantId(String serviceId, String tenantId);
}
