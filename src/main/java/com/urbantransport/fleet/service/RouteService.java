package com.urbantransport.fleet.service;

import com.urbantransport.fleet.dto.CreateRouteRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.RouteResponse;
import com.urbantransport.fleet.dto.UpdateRouteRequest;

import java.util.UUID;

public interface RouteService {

    RouteResponse create(CreateRouteRequest request, UUID originBranchId, UUID createdBy);

    RouteResponse update(UUID id, UpdateRouteRequest request, UUID branchId);

    void delete(UUID id, UUID branchId);

    PageResponse<RouteResponse> search(UUID originBranchId, UUID destinationBranchId, int page, int size);

    RouteResponse findById(UUID id);
}
