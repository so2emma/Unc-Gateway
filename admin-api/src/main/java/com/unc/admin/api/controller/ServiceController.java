package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.service.ServiceManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/services")
public class ServiceController {

    private final ServiceManagementService serviceManagementService;
    private final TenantScopeGuard tenantScopeGuard;

    public ServiceController(ServiceManagementService serviceManagementService, TenantScopeGuard tenantScopeGuard) {
        this.serviceManagementService = serviceManagementService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<ServiceDto> createService(@RequestBody ServiceDto dto) {
        tenantScopeGuard.checkTenantScope();
        ServiceDto created = serviceManagementService.createService(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ServiceDto> listServices() {
        tenantScopeGuard.checkTenantScope();
        return serviceManagementService.listServices();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ServiceDto getService(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        return serviceManagementService.getService(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ServiceDto updateService(@PathVariable("id") UUID id, @RequestBody ServiceDto dto) {
        tenantScopeGuard.checkTenantScope();
        return serviceManagementService.updateService(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<Void> deleteService(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        serviceManagementService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
