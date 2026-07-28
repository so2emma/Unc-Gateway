package com.unc.admin.api.controller;

import com.unc.admin.api.dto.ServiceDto;
import com.unc.admin.api.service.ServiceManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/services")
public class ServiceController {

    private final ServiceManagementService serviceManagementService;

    public ServiceController(ServiceManagementService serviceManagementService) {
        this.serviceManagementService = serviceManagementService;
    }

    @PostMapping
    public ResponseEntity<ServiceDto> createService(@RequestBody ServiceDto dto) {
        ServiceDto created = serviceManagementService.createService(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<ServiceDto> listServices() {
        return serviceManagementService.listServices();
    }

    @GetMapping("/{id}")
    public ServiceDto getService(@PathVariable("id") String id) {
        return serviceManagementService.getService(id);
    }

    @PutMapping("/{id}")
    public ServiceDto updateService(@PathVariable("id") String id, @RequestBody ServiceDto dto) {
        return serviceManagementService.updateService(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable("id") String id) {
        serviceManagementService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
