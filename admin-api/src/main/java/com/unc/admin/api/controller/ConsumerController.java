package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ConsumerDto;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.service.ConsumerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/consumers")
public class ConsumerController {

    private final ConsumerService consumerService;
    private final TenantScopeGuard tenantScopeGuard;

    public ConsumerController(ConsumerService consumerService, TenantScopeGuard tenantScopeGuard) {
        this.consumerService = consumerService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<ConsumerDto> createConsumer(@RequestBody ConsumerDto dto) {
        tenantScopeGuard.checkTenantScope();
        ConsumerDto created = consumerService.createConsumer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ConsumerDto> listConsumers() {
        tenantScopeGuard.checkTenantScope();
        return consumerService.listConsumers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ConsumerDto getConsumer(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        return consumerService.getConsumer(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ConsumerDto updateConsumer(@PathVariable("id") UUID id, @RequestBody ConsumerDto dto) {
        tenantScopeGuard.checkTenantScope();
        return consumerService.updateConsumer(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<Void> deleteConsumer(@PathVariable("id") UUID id) {
        tenantScopeGuard.checkTenantScope();
        consumerService.deleteConsumer(id);
        return ResponseEntity.noContent().build();
    }
}
