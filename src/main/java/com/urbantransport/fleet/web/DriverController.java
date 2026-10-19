package com.urbantransport.fleet.web;

import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverCreatedResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    // TODO: subida de foto multipart del chofer (guardar en app.storage.photos-dir y actualizar photoUrl)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public DriverCreatedResponse register(@Valid @RequestBody CreateDriverRequest request, @AuthenticationPrincipal Jwt jwt) {
        return driverService.register(request, branchIdOf(jwt));
    }

    private UUID branchIdOf(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branchId");
        if (branchId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_REQUIRED, "El usuario no tiene sucursal asignada");
        }
        return UUID.fromString(branchId);
    }
}
