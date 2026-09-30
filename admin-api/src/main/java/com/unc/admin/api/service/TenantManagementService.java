package com.unc.admin.api.service;

import com.unc.admin.api.dto.CreateTenantRequest;
import com.unc.admin.api.dto.TenantDto;

import java.util.List;
import java.util.UUID;

public interface TenantManagementService {
    TenantDto createTenant(CreateTenantRequest request);
    List<TenantDto> listTenants();
    TenantDto getTenant(UUID id);
    TenantDto updateTenant(UUID id, TenantDto dto);
    void deleteTenant(UUID id);
}
