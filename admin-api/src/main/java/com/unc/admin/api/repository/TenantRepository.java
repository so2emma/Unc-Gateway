package com.unc.admin.api.repository;

import com.unc.admin.api.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findByApiKey(String apiKey);

    Optional<TenantEntity> findByIdAndApiKey(UUID id, String apiKey);

    boolean existsByIdAndApiKeyAndStatus(UUID id, String apiKey, String status);
}
