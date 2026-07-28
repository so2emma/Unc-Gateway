package com.unc.admin.api.service;

import com.unc.admin.api.dto.ServiceDto;

import java.util.List;

public interface ServiceManagementService {
    ServiceDto createService(ServiceDto dto);
    List<ServiceDto> listServices();
    ServiceDto getService(String id);
    ServiceDto updateService(String id, ServiceDto dto);
    void deleteService(String id);
}
