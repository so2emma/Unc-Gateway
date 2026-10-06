package com.unc.admin.api.controller;

import com.unc.admin.api.dto.RouteDto;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.service.RouteManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/routes")
public class RouteController {

    private final RouteManagementService routeManagementService;
    private final TenantScopeGuard tenantScopeGuard;

    public RouteController(RouteManagementService routeManagementService, TenantScopeGuard tenantScopeGuard) {
        this.routeManagementService = routeManagementService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<RouteDto> createRoute(@RequestBody RouteDto dto) {
        tenantScopeGuard.checkTenantScope();
        RouteDto created = routeManagementService.createRoute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RouteDto> listRoutes() {
        tenantScopeGuard.checkTenantScope();
        return routeManagementService.listRoutes();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RouteDto getRoute(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        return routeManagementService.getRoute(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public RouteDto updateRoute(@PathVariable("id") UUID id, @RequestBody RouteDto dto) {
        tenantScopeGuard.checkTenantScope();
        return routeManagementService.updateRoute(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<Void> deleteRoute(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        routeManagementService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }
}
