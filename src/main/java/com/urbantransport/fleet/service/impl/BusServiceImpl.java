package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateBusRequest;
import com.urbantransport.fleet.event.BusEventPayload;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BusRepository;
import com.urbantransport.fleet.repository.BusSpecifications;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.BusService;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusServiceImpl implements BusService {

    private static final int MIN_MANUFACTURE_YEAR = 1950;
    private static final int MAX_PAGE_SIZE = 100;

    private final BusRepository busRepository;
    private final BranchProjectionService branchProjectionService;
    private final OutboxService outboxService;
    private final PhotoStorageService photoStorageService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public BusResponse create(CreateBusRequest request, UUID branchId) {
        validateCapacity(request.capacity());
        validateSeatLayout(request.capacity(), request.seatRows(), request.seatColumns(), request.aisleAfterColumn());
        validateManufactureYear(request.manufactureYear());
        if (request.status() == BusStatus.EN_TRANSITO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_STATUS,
                    "Un bus nuevo no puede registrarse EN_TRANSITO");
        }

        String plate = normalizePlate(request.plate());
        if (busRepository.existsByPlateIgnoreCase(plate)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.PLATE_ALREADY_EXISTS,
                    "Ya existe un bus con la placa " + plate);
        }

        branchProjectionService.assertActive(branchId);

        BusEntity bus = busRepository.save(BusEntity.builder()
                .plate(plate)
                .brand(request.brand().trim())
                .model(request.model().trim())
                .manufactureYear(request.manufactureYear().shortValue())
                .capacity(request.capacity())
                .seatRows(request.seatRows().shortValue())
                .seatColumns(request.seatColumns().shortValue())
                .aisleAfterColumn(toShort(request.aisleAfterColumn()))
                .status(request.status())
                .odometerKm(request.odometerKm() != null ? request.odometerKm() : BigDecimal.ZERO)
                .branchId(branchId)
                .build());

        outboxService.record(EventTypes.AGGREGATE_BUS, bus.getId(), EventTypes.BUS_REGISTERED, BusEventPayload.fromEntity(bus));

        return toResponse(bus);
    }

    @Override
    @Transactional
    public BusResponse update(UUID id, UpdateBusRequest request, UUID branchId) {
        BusEntity bus = getOrThrow(id);
        assertBelongsTo(bus, branchId);
        if (bus.getStatus() == BusStatus.INACTIVO) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.BUS_INACTIVE, "El bus está inactivo");
        }

        validateCapacity(request.capacity());
        validateSeatLayout(request.capacity(), request.seatRows(), request.seatColumns(), request.aisleAfterColumn());
        validateManufactureYear(request.manufactureYear());
        BusStatus newStatus = resolveNewStatus(bus.getStatus(), request.status());

        String plate = normalizePlate(request.plate());
        if (busRepository.existsByPlateIgnoreCaseAndIdNot(plate, bus.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.PLATE_ALREADY_EXISTS,
                    "Ya existe un bus con la placa " + plate);
        }

        bus.setPlate(plate);
        bus.setBrand(request.brand().trim());
        bus.setModel(request.model().trim());
        bus.setManufactureYear(request.manufactureYear().shortValue());
        bus.setCapacity(request.capacity());
        bus.setSeatRows(request.seatRows().shortValue());
        bus.setSeatColumns(request.seatColumns().shortValue());
        bus.setAisleAfterColumn(toShort(request.aisleAfterColumn()));
        bus.setStatus(newStatus);

        outboxService.record(EventTypes.AGGREGATE_BUS, bus.getId(), EventTypes.BUS_UPDATED, BusEventPayload.fromEntity(bus));

        return toResponse(bus);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BusResponse> search(BusStatus status, UUID branchId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(busRepository
                .findAll(BusSpecifications.filter(status, branchId), pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public BusResponse findById(UUID id, UUID scopeBranchId) {
        BusEntity bus = getOrThrow(id);
        if (scopeBranchId != null) {
            assertBelongsTo(bus, scopeBranchId);
        }
        return toResponse(bus);
    }

    @Override
    @Transactional
    public BusResponse deactivate(UUID id, UUID branchId) {
        BusEntity bus = getOrThrow(id);
        assertBelongsTo(bus, branchId);

        if (bus.getStatus() == BusStatus.INACTIVO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_INACTIVE,
                    "El bus ya fue desactivado");
        }
        // TODO: consultar a trip-service si el bus tiene viajes programados o en tránsito; 422 BUS_HAS_ACTIVE_TRIPS si tiene, 503 si trip-service no responde
        if (bus.getStatus() == BusStatus.EN_TRANSITO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_HAS_ACTIVE_TRIPS,
                    "El bus tiene viajes programados o en tránsito");
        }

        bus.setStatus(BusStatus.INACTIVO);
        outboxService.record(EventTypes.AGGREGATE_BUS, bus.getId(), EventTypes.BUS_DEACTIVATED, BusEventPayload.fromEntity(bus));

        return toResponse(bus);
    }

    @Override
    public BusResponse uploadPhoto(UUID id, MultipartFile file, UUID branchId) {
        BusEntity bus = getOrThrow(id);
        assertBelongsTo(bus, branchId);
        if (bus.getStatus() == BusStatus.INACTIVO) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.BUS_INACTIVE, "El bus está inactivo");
        }

        String previousKey = bus.getPhotoUrl();
        String key = photoStorageService.upload(PhotoOwner.BUS, bus.getId(), file);

        BusEntity updated;
        try {
            updated = transactionTemplate.execute(status -> {
                BusEntity current = getOrThrow(id);
                current.setPhotoUrl(key);
                return current;
            });
        } catch (RuntimeException exception) {
            photoStorageService.delete(key);
            throw exception;
        }

        photoStorageService.delete(previousKey);
        return toResponse(updated);
    }

    private BusResponse toResponse(BusEntity bus) {
        return BusResponse.fromEntity(bus, photoStorageService.presignedUrl(bus.getPhotoUrl()));
    }

    private BusEntity getOrThrow(UUID id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND, "Bus no encontrado"));
    }

    private void assertBelongsTo(BusEntity bus, UUID branchId) {
        if (!bus.getBranchId().equals(branchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    "El bus no pertenece a tu sucursal");
        }
    }

    private BusStatus resolveNewStatus(BusStatus current, BusStatus requested) {
        if (requested == null || requested == current) {
            return current;
        }
        if (requested != BusStatus.DISPONIBLE && requested != BusStatus.EN_TALLER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_STATUS,
                    "Solo se puede cambiar el estado a DISPONIBLE o EN_TALLER");
        }
        if (current == BusStatus.EN_TRANSITO) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_IN_TRANSIT,
                    "El bus está en un viaje en tránsito y no se puede cambiar su estado");
        }
        return requested;
    }

    private String normalizePlate(String plate) {
        return plate.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCapacity(Integer capacity) {
        if (capacity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CAPACITY,
                    "La capacidad debe ser mayor a cero");
        }
    }

    private void validateSeatLayout(Integer capacity, Integer seatRows, Integer seatColumns, Integer aisleAfterColumn) {
        int seats = seatRows * seatColumns;
        if (seats != capacity) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CAPACITY,
                    "La capacidad debe ser igual a filas × columnas (" + seats + ")");
        }
        if (aisleAfterColumn != null && aisleAfterColumn >= seatColumns) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_SEAT_LAYOUT,
                    "El pasillo debe ir después de una columna entre 1 y " + (seatColumns - 1));
        }
    }

    private Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    private void validateManufactureYear(Integer year) {
        int maxYear = Year.now(zone).getValue() + 1;
        if (year < MIN_MANUFACTURE_YEAR || year > maxYear) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_YEAR,
                    "El año debe estar entre " + MIN_MANUFACTURE_YEAR + " y " + maxYear);
        }
    }
}
