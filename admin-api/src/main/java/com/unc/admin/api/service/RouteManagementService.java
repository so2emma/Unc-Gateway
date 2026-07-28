package com.unc.admin.api.service;

import com.unc.admin.api.dto.RouteDto;

import java.util.List;

public interface RouteManagementService {
    RouteDto createRoute(RouteDto dto);
    List<RouteDto> listRoutes();
    RouteDto getRoute(String id);
    RouteDto updateRoute(String id, RouteDto dto);
    void deleteRoute(String id);
}
