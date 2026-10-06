package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ConsumerKeyDto;
import com.unc.admin.api.dto.IssueConsumerKeyRequest;
import com.unc.admin.api.security.TenantScopeGuard;
import com.unc.admin.api.service.ConsumerKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/consumers/{consumerId}/keys")
public class ConsumerKeyController {

    private final ConsumerKeyService consumerKeyService;
    private final TenantScopeGuard tenantScopeGuard;

    public ConsumerKeyController(ConsumerKeyService consumerKeyService, TenantScopeGuard tenantScopeGuard) {
        this.consumerKeyService = consumerKeyService;
        this.tenantScopeGuard = tenantScopeGuard;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<ConsumerKeyDto> issueKey(
            @PathVariable("consumerId") UUID consumerId,
            @RequestBody(required = false) IssueConsumerKeyRequest request) {
        tenantScopeGuard.checkTenantScope();
        ConsumerKeyDto issued = consumerKeyService.issueKey(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(issued);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ConsumerKeyDto> listKeys(@PathVariable("consumerId") UUID consumerId) {
        tenantScopeGuard.checkTenantScope();
        return consumerKeyService.listKeys(consumerId);
    }

    @DeleteMapping("/{keyId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ResponseEntity<Void> revokeKey(
            @PathVariable("consumerId") UUID consumerId,
            @PathVariable("keyId") UUID keyId) {
        tenantScopeGuard.checkTenantScope();
        consumerKeyService.revokeKey(consumerId, keyId);
        return ResponseEntity.noContent().build();
    }
}
