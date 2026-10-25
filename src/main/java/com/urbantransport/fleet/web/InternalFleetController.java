package com.urbantransport.fleet.web;

import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.DriverResponse;
import com.urbantransport.fleet.dto.InternalRouteResponse;
import com.urbantransport.fleet.service.BusService;
import com.urbantransport.fleet.service.DriverService;
import com.urbantransport.fleet.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalFleetController {

    private final BusService busService;
    private final DriverService driverService;
    private final RouteService routeService;

    @GetMapping("/buses/{id}")
    public BusResponse findBus(@PathVariable UUID id) {
        return busService.findById(id, null);
    }

    @GetMapping("/drivers/{id}")
    public DriverResponse findDriver(@PathVariable UUID id) {
        return driverService.findById(id, null);
    }

    @GetMapping("/drivers/by-user/{userId}")
    public DriverResponse findDriverByUser(@PathVariable UUID userId) {
        return driverService.findByUserId(userId);
    }

    @GetMapping("/routes/{id}")
    public InternalRouteResponse findRoute(@PathVariable UUID id) {
        return routeService.findInternalById(id);
    }
}
