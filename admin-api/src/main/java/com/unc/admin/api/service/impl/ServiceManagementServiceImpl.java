package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.entity.ServiceEntity;
import com.unc.admin.api.repository.ServiceRepository;
import com.unc.admin.api.service.ServiceManagementService;
import com.unc.admin.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceManagementServiceImpl implements ServiceManagementService {

    private final ServiceRepository serviceRepository;

    public ServiceManagementServiceImpl(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public ServiceDto createService(ServiceDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }
        String url = dto.getUrl() != null ? dto.getUrl() : dto.getUpstreamUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service URL is required");
        }

        ServiceEntity entity = new ServiceEntity();
        entity.setTenantId(tenantId);
        entity.setName(dto.getName());
        entity.setUrl(url);
        if (dto.getConnectTimeout() != null) {
            entity.setConnectTimeout(dto.getConnectTimeout());
        }
        if (dto.getReadTimeout() != null) {
            entity.setReadTimeout(dto.getReadTimeout());
        }

        ServiceEntity saved = serviceRepository.save(entity);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDto> listServices() {
        UUID tenantId = TenantContext.getTenantId();
        return serviceRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceDto getService(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return serviceRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    @Override
    public ServiceDto updateService(UUID id, ServiceDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        ServiceEntity entity = serviceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        String url = dto.getUrl() != null ? dto.getUrl() : dto.getUpstreamUrl();
        if (url != null) {
            entity.setUrl(url);
        }
        if (dto.getConnectTimeout() != null) {
            entity.setConnectTimeout(dto.getConnectTimeout());
        }
        if (dto.getReadTimeout() != null) {
            entity.setReadTimeout(dto.getReadTimeout());
        }

        ServiceEntity updated = serviceRepository.save(entity);
        return toDto(updated);
    }

    @Override
    public void deleteService(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        if (!serviceRepository.existsByIdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found");
        }
        serviceRepository.deleteByIdAndTenantId(id, tenantId);
    }

    private ServiceDto toDto(ServiceEntity entity) {
        ServiceDto dto = new ServiceDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setUrl(entity.getUrl());
        dto.setConnectTimeout(entity.getConnectTimeout());
        dto.setReadTimeout(entity.getReadTimeout());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
