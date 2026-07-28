package com.unc.admin.api.service;

import com.unc.admin.api.dto.ServiceDto;

import java.util.List;
import java.util.UUID;

public interface ServiceManagementService {
    ServiceDto createService(ServiceDto dto);
    List<ServiceDto> listServices();
    ServiceDto getService(UUID id);
    ServiceDto updateService(UUID id, ServiceDto dto);
    void deleteService(UUID id);
}
