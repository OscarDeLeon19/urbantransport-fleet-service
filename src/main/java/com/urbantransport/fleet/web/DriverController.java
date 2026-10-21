package com.urbantransport.fleet.web;

import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverCreatedResponse;
import com.urbantransport.fleet.dto.DriverResponse;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateDriverRequest;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.service.DriverService;
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
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final DriverService driverService;

    // TODO: subida de foto multipart del chofer (guardar en app.storage.photos-dir y actualizar photoUrl)
    // TODO: endpoint interno GET /internal/drivers/{id}/validate para trip-service, protegido con X-Internal-Key (activo, licencia vigente y sucursal)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public DriverCreatedResponse register(@Valid @RequestBody CreateDriverRequest request, @AuthenticationPrincipal Jwt jwt) {
        return driverService.register(request, branchIdOf(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public DriverResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDriverRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return driverService.update(id, request, branchIdOf(jwt));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public DriverResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return driverService.deactivate(id, branchIdOf(jwt));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public PageResponse<DriverResponse> search(@RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) Integer expiringInDays,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal Jwt jwt) {
        return driverService.search(active, expiringInDays, resolveBranchFilter(jwt, branchId), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public DriverResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return driverService.findById(id, isSystemAdmin(jwt) ? null : branchIdOf(jwt));
    }

    private UUID resolveBranchFilter(Jwt jwt, UUID requestedBranchId) {
        if (isSystemAdmin(jwt)) {
            return requestedBranchId;
        }
        UUID ownBranchId = branchIdOf(jwt);
        if (requestedBranchId != null && !requestedBranchId.equals(ownBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_FORBIDDEN,
                    "Solo puedes consultar choferes de tu sucursal");
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
