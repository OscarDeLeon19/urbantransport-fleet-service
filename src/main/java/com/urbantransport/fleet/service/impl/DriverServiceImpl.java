package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.client.IdentityClient;
import com.urbantransport.fleet.client.IdentityCreateUserRequest;
import com.urbantransport.fleet.client.IdentityUserResponse;
import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseAlertEntity;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverCreatedResponse;
import com.urbantransport.fleet.dto.DriverResponse;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateDriverRequest;
import com.urbantransport.fleet.event.DriverEventPayload;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.DriverRepository;
import com.urbantransport.fleet.repository.DriverSpecifications;
import com.urbantransport.fleet.repository.LicenseAlertRepository;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.DriverService;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.service.PhotoStorageService;
import com.urbantransport.fleet.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private static final List<Integer> ALERT_DAYS_BEFORE = List.of(30, 15, 3);
    private static final int MAX_PAGE_SIZE = 100;

    private final DriverRepository driverRepository;
    private final LicenseAlertRepository licenseAlertRepository;
    private final BranchProjectionService branchProjectionService;
    private final OutboxService outboxService;
    private final IdentityClient identityClient;
    private final TransactionTemplate transactionTemplate;
    private final PhotoStorageService photoStorageService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    public DriverCreatedResponse register(CreateDriverRequest request, UUID branchId) {
        LocalDate today = LocalDate.now(zone);
        if (request.licenseExpiresOn().isBefore(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.LICENSE_EXPIRED,
                    "La licencia ya está vencida");
        }
        String licenseNumber = request.licenseNumber().trim().toUpperCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        UUID driverId = transactionTemplate.execute(status ->
                prepare(request, licenseNumber, email, branchId, today).getId());

        String temporaryPassword = PasswordGenerator.temporary();
        IdentityUserResponse account;
        try {
            account = identityClient.createDriverUser(new IdentityCreateUserRequest(
                    email, temporaryPassword, request.fullName().trim()));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                licenseAlertRepository.deleteByDriverId(driverId);
                driverRepository.deleteById(driverId);
            });
            throw exception;
        }

        try {
            DriverEntity driver = transactionTemplate.execute(status -> complete(driverId, account));
            return new DriverCreatedResponse(toResponse(driver), temporaryPassword);
        } catch (RuntimeException exception) {
            log.error("Cuenta {} creada en identity para el chofer {} pero no se pudo completar el registro local",
                    account.id(), driverId, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public DriverResponse update(UUID id, UpdateDriverRequest request, UUID branchId) {
        DriverEntity driver = getOwnedOrThrow(id, branchId);
        if (!driver.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.DRIVER_INACTIVE, "El chofer está inactivo");
        }

        LocalDate today = LocalDate.now(zone);
        boolean expirationChanged = !request.licenseExpiresOn().equals(driver.getLicenseExpiresOn());
        if (expirationChanged && request.licenseExpiresOn().isBefore(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.LICENSE_EXPIRED,
                    "La licencia ya está vencida");
        }

        String licenseNumber = request.licenseNumber().trim().toUpperCase(Locale.ROOT);
        if (driverRepository.existsByLicenseNumberIgnoreCaseAndIdNot(licenseNumber, driver.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.LICENSE_ALREADY_EXISTS,
                    "Ya existe un chofer con la licencia " + licenseNumber);
        }

        driver.setLicenseNumber(licenseNumber);
        driver.setLicenseType(request.licenseType());
        driver.setLicenseExpiresOn(request.licenseExpiresOn());
        driver.setPhone(request.phone().trim());
        driver.setBaseSalaryPerTrip(request.baseSalaryPerTrip());

        if (expirationChanged) {
            licenseAlertRepository.deletePendingByDriverId(driver.getId());
            createLicenseAlerts(driver, today);
        }

        outboxService.record(EventTypes.AGGREGATE_DRIVER, driver.getId(), EventTypes.DRIVER_UPDATED,
                DriverEventPayload.fromEntity(driver));

        return toResponse(driver);
    }

    @Override
    @Transactional
    public DriverResponse deactivate(UUID id, UUID branchId) {
        DriverEntity driver = getOwnedOrThrow(id, branchId);
        if (!driver.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DRIVER_INACTIVE,
                    "El chofer ya fue desactivado");
        }
        // TODO: consultar a trip-service si el chofer tiene viajes programados o en tránsito; 422 DRIVER_HAS_ACTIVE_TRIPS si tiene, 503 si trip-service no responde

        driver.setActive(false);
        outboxService.record(EventTypes.AGGREGATE_DRIVER, driver.getId(), EventTypes.DRIVER_DEACTIVATED,
                DriverEventPayload.fromEntity(driver));

        return toResponse(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> search(Boolean active, Integer expiringInDays, UUID branchId,
                                               int page, int size) {
        LocalDate expiresFrom = null;
        LocalDate expiresTo = null;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (expiringInDays != null) {
            if (expiringInDays < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                        "expiringInDays no puede ser negativo");
            }
            expiresFrom = LocalDate.now(zone);
            expiresTo = expiresFrom.plusDays(expiringInDays);
            sort = Sort.by(Sort.Direction.ASC, "licenseExpiresOn");
        }
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                sort);
        return PageResponse.of(driverRepository
                .findAll(DriverSpecifications.filter(active, expiresFrom, expiresTo, branchId), pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverResponse findById(UUID id, UUID scopeBranchId) {
        if (scopeBranchId != null) {
            return toResponse(getOwnedOrThrow(id, scopeBranchId));
        }
        return toResponse(getOrThrow(id));
    }

    @Override
    public DriverResponse uploadPhoto(UUID id, MultipartFile file, UUID branchId) {
        DriverEntity driver = getOwnedOrThrow(id, branchId);
        if (!driver.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.DRIVER_INACTIVE, "El chofer está inactivo");
        }

        String previousKey = driver.getPhotoUrl();
        String key = photoStorageService.upload(PhotoOwner.DRIVER, driver.getId(), file);

        DriverEntity updated;
        try {
            updated = transactionTemplate.execute(status -> {
                DriverEntity current = getOrThrow(id);
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

    private DriverResponse toResponse(DriverEntity driver) {
        return DriverResponse.fromEntity(driver, photoStorageService.presignedUrl(driver.getPhotoUrl()));
    }

    private DriverEntity getOrThrow(UUID id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.DRIVER_NOT_FOUND, "Chofer no encontrado"));
    }

    private DriverEntity getOwnedOrThrow(UUID id, UUID branchId) {
        DriverEntity driver = getOrThrow(id);
        if (!driver.getBranchId().equals(branchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.DRIVER_NOT_IN_BRANCH,
                    "El chofer no pertenece a tu sucursal");
        }
        return driver;
    }

    private DriverEntity complete(UUID driverId, IdentityUserResponse account) {
        DriverEntity driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar el registro"));
        driver.setUserId(account.id());
        recordRegistered(driver);
        return driver;
    }

    private DriverEntity prepare(CreateDriverRequest request, String licenseNumber, String email,
                                 UUID branchId, LocalDate today) {
        if (driverRepository.existsByLicenseNumberIgnoreCase(licenseNumber)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.LICENSE_ALREADY_EXISTS,
                    "Ya existe un chofer con la licencia " + licenseNumber);
        }
        branchProjectionService.assertActive(branchId);

        DriverEntity driver = driverRepository.save(DriverEntity.builder()
                .fullName(request.fullName().trim())
                .licenseNumber(licenseNumber)
                .licenseType(request.licenseType())
                .licenseExpiresOn(request.licenseExpiresOn())
                .phone(request.phone().trim())
                .email(email)
                .baseSalaryPerTrip(request.baseSalaryPerTrip())
                .branchId(branchId)
                .build());

        createLicenseAlerts(driver, today);
        return driver;
    }

    private void createLicenseAlerts(DriverEntity driver, LocalDate today) {
        LocalDate expiresOn = driver.getLicenseExpiresOn();
        List<LicenseAlertEntity> alerts = ALERT_DAYS_BEFORE.stream()
                .map(days -> LicenseAlertEntity.builder()
                        .driverId(driver.getId())
                        .daysBefore(days.shortValue())
                        .expiresOn(expiresOn)
                        .notifyOn(expiresOn.minusDays(days))
                        .build())
                .filter(alert -> alert.getNotifyOn().isAfter(today))
                .toList();
        licenseAlertRepository.saveAll(alerts);
    }

    private void recordRegistered(DriverEntity driver) {
        outboxService.record(EventTypes.AGGREGATE_DRIVER, driver.getId(), EventTypes.DRIVER_REGISTERED,
                DriverEventPayload.fromEntity(driver));
    }
}
