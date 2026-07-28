package com.unc.admin.api.service.impl;

import com.unc.admin.api.dto.RouteDto;
import com.unc.admin.api.entity.RouteEntity;
import com.unc.admin.api.repository.RouteRepository;
import com.unc.admin.api.repository.ServiceRepository;
import com.unc.admin.api.service.RouteManagementService;
import com.unc.admin.api.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RouteManagementServiceImpl implements RouteManagementService {

    private final RouteRepository routeRepository;
    private final ServiceRepository serviceRepository;

    public RouteManagementServiceImpl(RouteRepository routeRepository, ServiceRepository serviceRepository) {
        this.routeRepository = routeRepository;
        this.serviceRepository = serviceRepository;
    }

    @Override
    public RouteDto createRoute(RouteDto dto) {
        String tenantId = TenantContext.getTenantId();
        if (dto.getServiceId() == null || dto.getServiceId().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceId is required");
        }
        String paths = dto.getPaths() != null ? dto.getPaths() : dto.getPath();
        if (paths == null || paths.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }

        if (!serviceRepository.existsByIdAndTenantId(dto.getServiceId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found for this tenant");
        }

        RouteEntity entity = new RouteEntity();
        entity.setTenantId(tenantId);
        entity.setServiceId(dto.getServiceId());
        entity.setName(dto.getName() != null ? dto.getName() : "route-" + paths.replaceAll("[^a-zA-Z0-9]", "-"));
        entity.setPaths(paths);
        entity.setMethods(dto.getMethods());
        entity.setProtocols(dto.getProtocols());
        if (dto.getStripPath() != null) {
            entity.setStripPath(dto.getStripPath());
        }

        RouteEntity saved = routeRepository.save(entity);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteDto> listRoutes() {
        String tenantId = TenantContext.getTenantId();
        return routeRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RouteDto getRoute(String id) {
        String tenantId = TenantContext.getTenantId();
        return routeRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));
    }

    @Override
    public RouteDto updateRoute(String id, RouteDto dto) {
        String tenantId = TenantContext.getTenantId();
        RouteEntity entity = routeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));

        if (dto.getServiceId() != null) {
            if (!serviceRepository.existsByIdAndTenantId(dto.getServiceId(), tenantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service not found for this tenant");
            }
            entity.setServiceId(dto.getServiceId());
        }
        String paths = dto.getPaths() != null ? dto.getPaths() : dto.getPath();
        if (paths != null) {
            entity.setPaths(paths);
        }
        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getMethods() != null) {
            entity.setMethods(dto.getMethods());
        }
        if (dto.getProtocols() != null) {
            entity.setProtocols(dto.getProtocols());
        }
        if (dto.getStripPath() != null) {
            entity.setStripPath(dto.getStripPath());
        }

        RouteEntity updated = routeRepository.save(entity);
        return toDto(updated);
    }

    @Override
    public void deleteRoute(String id) {
        String tenantId = TenantContext.getTenantId();
        RouteEntity entity = routeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));
        routeRepository.deleteByIdAndTenantId(entity.getId(), tenantId);
    }

    private RouteDto toDto(RouteEntity entity) {
        RouteDto dto = new RouteDto();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setServiceId(entity.getServiceId());
        dto.setName(entity.getName());
        dto.setPaths(entity.getPaths());
        dto.setMethods(entity.getMethods());
        dto.setProtocols(entity.getProtocols());
        dto.setStripPath(entity.getStripPath());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
