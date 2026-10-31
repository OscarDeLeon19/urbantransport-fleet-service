package com.urbantransport.fleet.web;

import com.urbantransport.fleet.dto.CreateRouteRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.RouteResponse;
import com.urbantransport.fleet.dto.UpdateRouteRequest;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public RouteResponse create(@Valid @RequestBody CreateRouteRequest request, @AuthenticationPrincipal Jwt jwt) {
        return routeService.create(request, branchIdOf(jwt), UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public RouteResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRouteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return routeService.update(id, request, branchIdOf(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        routeService.delete(id, branchIdOf(jwt));
    }

    @GetMapping
    public PageResponse<RouteResponse> search(@RequestParam(required = false) UUID originBranchId,
            @RequestParam(required = false) UUID destinationBranchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return routeService.search(originBranchId, destinationBranchId, page, size);
    }

    @GetMapping("/{id}")
    public RouteResponse findById(@PathVariable UUID id) {
        return routeService.findById(id);
    }

    private UUID branchIdOf(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branchId");
        if (branchId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_REQUIRED,
                    "El usuario no tiene sucursal asignada");
        }
        return UUID.fromString(branchId);
    }
}
