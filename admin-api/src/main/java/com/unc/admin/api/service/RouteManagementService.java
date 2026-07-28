package com.unc.admin.api.service;

import com.unc.admin.api.dto.RouteDto;

import java.util.List;
import java.util.UUID;

public interface RouteManagementService {
    RouteDto createRoute(RouteDto dto);
    List<RouteDto> listRoutes();
    RouteDto getRoute(UUID id);
    RouteDto updateRoute(UUID id, RouteDto dto);
    void deleteRoute(UUID id);
}
