package com.unc.admin.api.repository;

import com.unc.admin.api.entity.AdminApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminApiKeyRepository extends JpaRepository<AdminApiKeyEntity, UUID> {
    Optional<AdminApiKeyEntity> findByKeyHash(String keyHash);
}
