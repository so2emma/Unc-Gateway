package com.unc.admin.api.controller;

import com.unc.admin.api.dto.RouteDto;
import com.unc.admin.api.service.RouteManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/routes")
public class RouteController {

    private final RouteManagementService routeManagementService;

    public RouteController(RouteManagementService routeManagementService) {
        this.routeManagementService = routeManagementService;
    }

    @PostMapping
    public ResponseEntity<RouteDto> createRoute(@RequestBody RouteDto dto) {
        RouteDto created = routeManagementService.createRoute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<RouteDto> listRoutes() {
        return routeManagementService.listRoutes();
    }

    @GetMapping("/{id}")
    public RouteDto getRoute(@PathVariable("id") UUID id) {
        return routeManagementService.getRoute(id);
    }

    @PutMapping("/{id}")
    public RouteDto updateRoute(@PathVariable("id") UUID id, @RequestBody RouteDto dto) {
        return routeManagementService.updateRoute(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable("id") UUID id) {
        routeManagementService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }
}
