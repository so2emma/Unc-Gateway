package com.unc.admin.api.controller;

import com.unc.admin.api.dto.CreateTenantRequest;
import com.unc.admin.api.dto.TenantDto;
import com.unc.admin.api.service.TenantManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantManagementService tenantManagementService;

    public TenantController(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    @PostMapping
    public ResponseEntity<TenantDto> createTenant(@RequestBody CreateTenantRequest request) {
        TenantDto created = tenantManagementService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<TenantDto> listTenants() {
        return tenantManagementService.listTenants();
    }

    @GetMapping("/{id}")
    public TenantDto getTenant(@PathVariable("id") UUID id) {
        return tenantManagementService.getTenant(id);
    }

    @PutMapping("/{id}")
    public TenantDto updateTenant(@PathVariable("id") UUID id, @RequestBody TenantDto dto) {
        return tenantManagementService.updateTenant(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable("id") UUID id) {
        tenantManagementService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
