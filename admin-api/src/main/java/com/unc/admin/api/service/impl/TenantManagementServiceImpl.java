package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.CreateTenantRequest;
import com.unc.admin.api.dto.TenantDto;
import com.unc.admin.api.entity.TenantEntity;
import com.unc.admin.api.repository.TenantRepository;
import com.unc.admin.api.service.TenantManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenantManagementServiceImpl implements TenantManagementService {

    private final TenantRepository tenantRepository;

    public TenantManagementServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public TenantDto createTenant(CreateTenantRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant name is required");
        }

        UUID id = UUID.randomUUID();
        String apiKey = "unc_live_sec_" + UUID.randomUUID().toString().replace("-", "");

        TenantEntity entity = new TenantEntity(id, request.getName().trim(), apiKey, request.getEmail());
        TenantEntity saved = tenantRepository.save(entity);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantDto> listTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TenantDto getTenant(UUID id) {
        return tenantRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    @Override
    public TenantDto updateTenant(UUID id, TenantDto dto) {
        TenantEntity entity = tenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            entity.setName(dto.getName().trim());
        }
        if (dto.getEmail() != null) {
            entity.setEmail(dto.getEmail());
        }
        if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
            entity.setStatus(dto.getStatus().trim().toUpperCase());
        }

        TenantEntity updated = tenantRepository.save(entity);
        return toDto(updated);
    }

    @Override
    public void deleteTenant(UUID id) {
        if (!tenantRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        tenantRepository.deleteById(id);
    }

    private TenantDto toDto(TenantEntity entity) {
        TenantDto dto = new TenantDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setApiKey(entity.getApiKey());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
