package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;
import com.urbantransport.fleet.event.BusEventPayload;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.BusRepository;
import com.urbantransport.fleet.service.BusService;
import com.urbantransport.fleet.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusServiceImpl implements BusService {

    private static final int MIN_MANUFACTURE_YEAR = 1950;

    private final BusRepository busRepository;
    private final BranchProjectionRepository branchProjectionRepository;
    private final OutboxService outboxService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public BusResponse create(CreateBusRequest request, UUID branchId) {
        validateCapacity(request.capacity());
        validateManufactureYear(request.manufactureYear());
        if (request.status() == BusStatus.EN_TRANSITO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_STATUS,
                    "Un bus nuevo no puede registrarse EN_TRANSITO");
        }

        String plate = request.plate().trim().toUpperCase(Locale.ROOT);
        if (busRepository.existsByPlateIgnoreCase(plate)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.PLATE_ALREADY_EXISTS,
                    "Ya existe un bus con la placa " + plate);
        }

        assertBranchActive(branchId);

        BusEntity bus = busRepository.save(BusEntity.builder()
                .plate(plate)
                .brand(request.brand().trim())
                .model(request.model().trim())
                .manufactureYear(request.manufactureYear().shortValue())
                .capacity(request.capacity())
                .status(request.status())
                .odometerKm(request.odometerKm() != null ? request.odometerKm() : BigDecimal.ZERO)
                .photoUrl(request.photoUrl())
                .branchId(branchId)
                .build());

        outboxService.record(EventTypes.AGGREGATE_BUS, bus.getId(), EventTypes.BUS_REGISTERED, BusEventPayload.fromEntity(bus));

        return BusResponse.fromEntity(bus);
    }

    private void validateCapacity(Integer capacity) {
        if (capacity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CAPACITY,
                    "La capacidad debe ser mayor a cero");
        }
    }

    /** Se acepta hasta el año siguiente al actual (modelos que salen antes de tiempo). */
    private void validateManufactureYear(Integer year) {
        int maxYear = Year.now(zone).getValue() + 1;
        if (year < MIN_MANUFACTURE_YEAR || year > maxYear) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_YEAR,
                    "El año debe estar entre " + MIN_MANUFACTURE_YEAR + " y " + maxYear);
        }
    }

    private void assertBranchActive(UUID branchId) {
        BranchProjectionEntity branch = branchProjectionRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.BRANCH_NOT_FOUND,
                        "Sucursal no encontrada"));
        if (!branch.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE, "La sucursal está inactiva");
        }
    }
}
