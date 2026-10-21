package com.urbantransport.fleet.web;

import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateBusRequest;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.service.BusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
public class BusController {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final BusService busService;

    // TODO: subida de foto multipart del bus (guardar en app.storage.photos-dir y actualizar photoUrl)
    // TODO: endpoint interno GET /internal/buses/{id}/validate para trip-service, protegido con X-Internal-Key

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public BusResponse create(@Valid @RequestBody CreateBusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return busService.create(request, branchIdOf(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public BusResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBusRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return busService.update(id, request, branchIdOf(jwt));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public BusResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return busService.deactivate(id, branchIdOf(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public PageResponse<BusResponse> search(@RequestParam(required = false) BusStatus status,
                                            @RequestParam(required = false) UUID branchId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        return busService.search(status, resolveBranchFilter(jwt, branchId), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public BusResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return busService.findById(id, isSystemAdmin(jwt) ? null : branchIdOf(jwt));
    }

    private UUID resolveBranchFilter(Jwt jwt, UUID requestedBranchId) {
        if (isSystemAdmin(jwt)) {
            return requestedBranchId;
        }
        UUID ownBranchId = branchIdOf(jwt);
        if (requestedBranchId != null && !requestedBranchId.equals(ownBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_FORBIDDEN,
                    "Solo puedes consultar buses de tu sucursal");
        }
        return ownBranchId;
    }

    private boolean isSystemAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(SYSTEM_ADMIN);
    }

    private UUID branchIdOf(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branchId");
        if (branchId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_REQUIRED, "El usuario no tiene sucursal asignada");
        }
        return UUID.fromString(branchId);
    }
}
