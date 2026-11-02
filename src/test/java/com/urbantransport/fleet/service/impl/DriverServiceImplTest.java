package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.client.IdentityClient;
import com.urbantransport.fleet.client.IdentityCreateUserRequest;
import com.urbantransport.fleet.client.IdentityUserResponse;
import com.urbantransport.fleet.client.TripClient;
import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseAlertEntity;
import com.urbantransport.fleet.domain.LicenseType;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverResponse;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateDriverRequest;
import com.urbantransport.fleet.event.DriverEventPayload;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.DriverRepository;
import com.urbantransport.fleet.repository.LicenseAlertRepository;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.service.PhotoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final UUID DRIVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String FULL_NAME = "Juan Pérez";
    private static final String LICENSE = "LIC-001";
    private static final String NEW_LICENSE = "LIC-002";
    private static final String PHONE = "55551234";
    private static final String EMAIL = "juan@mail.com";
    private static final BigDecimal SALARY = new BigDecimal("150.00");
    private static final BigDecimal NEW_SALARY = new BigDecimal("175.50");
    private static final LocalDate EXPIRES_ON = TODAY.plusDays(90);

    private static final String OLD_PHOTO_KEY = "drivers/old.jpg";
    private static final String NEW_PHOTO_KEY = "drivers/new.jpg";
    private static final String PRESIGNED_URL = "https://s3.amazonaws.com/presigned";

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private LicenseAlertRepository licenseAlertRepository;

    @Mock
    private BranchProjectionService branchProjectionService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private IdentityClient identityClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private TripClient tripClient;

    @Captor
    private ArgumentCaptor<DriverEntity> driverCaptor;

    @Captor
    private ArgumentCaptor<IdentityCreateUserRequest> identityRequestCaptor;

    @Captor
    private ArgumentCaptor<List<LicenseAlertEntity>> alertsCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private DriverServiceImpl driverService;

    private DriverEntity persisted;

    @BeforeEach
    void setUp() {
        driverService = new DriverServiceImpl(driverRepository, licenseAlertRepository, branchProjectionService,
                outboxService, identityClient, transactionTemplate, photoStorageService, tripClient);
        ReflectionTestUtils.setField(driverService, "zone", ZONE);
    }

    @Nested
    class Register {

        @Test
        void validRequest_createsDriverAccountAndRecordsEvent() {
            runTransactionsInline();
            persistInMemory();
            when(driverRepository.findById(DRIVER_ID)).thenAnswer(invocation -> Optional.ofNullable(persisted));
            when(identityClient.createDriverUser(any())).thenReturn(account());

            DriverResponse response = driverService.register(createRequest(EXPIRES_ON), BRANCH_ID);

            verify(branchProjectionService).assertActive(BRANCH_ID);
            verify(driverRepository).save(driverCaptor.capture());
            DriverEntity saved = driverCaptor.getValue();
            assertEquals(FULL_NAME, saved.getFullName());
            assertEquals(LICENSE, saved.getLicenseNumber());
            assertEquals(LicenseType.A, saved.getLicenseType());
            assertEquals(EXPIRES_ON, saved.getLicenseExpiresOn());
            assertEquals(PHONE, saved.getPhone());
            assertEquals(EMAIL, saved.getEmail());
            assertEquals(SALARY, saved.getBaseSalaryPerTrip());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertTrue(saved.isActive());
            assertEquals(USER_ID, saved.getUserId());

            verify(identityClient).createDriverUser(identityRequestCaptor.capture());
            IdentityCreateUserRequest identityRequest = identityRequestCaptor.getValue();
            assertEquals(EMAIL, identityRequest.email());
            assertEquals(FULL_NAME, identityRequest.fullName());

            verify(outboxService).record(EventTypes.AGGREGATE_DRIVER, DRIVER_ID, EventTypes.DRIVER_REGISTERED,
                    DriverEventPayload.fromEntity(saved));
            assertEquals(DRIVER_ID, response.getId());
            assertEquals(USER_ID, response.getUserId());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.fleet.service.impl.DriverServiceImplTest#alertsByDaysToExpire")
        void licenseExpiration_createsOnlyFutureAlerts(int daysToExpire, List<Integer> expectedDaysBefore) {
            runTransactionsInline();
            persistInMemory();
            when(driverRepository.findById(DRIVER_ID)).thenAnswer(invocation -> Optional.ofNullable(persisted));
            when(identityClient.createDriverUser(any())).thenReturn(account());
            LocalDate expiresOn = TODAY.plusDays(daysToExpire);

            driverService.register(createRequest(expiresOn), BRANCH_ID);

            verify(licenseAlertRepository).saveAll(alertsCaptor.capture());
            List<LicenseAlertEntity> alerts = alertsCaptor.getValue();
            assertEquals(expectedDaysBefore, alerts.stream().map(alert -> alert.getDaysBefore().intValue()).toList());
            alerts.forEach(alert -> {
                assertEquals(DRIVER_ID, alert.getDriverId());
                assertEquals(expiresOn, alert.getExpiresOn());
                assertEquals(expiresOn.minusDays(alert.getDaysBefore()), alert.getNotifyOn());
                assertNull(alert.getSentAt());
            });
        }

        @Test
        void expiredLicense_throwsLicenseExpired() {
            CreateDriverRequest request = createRequest(TODAY.minusDays(1));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.LICENSE_EXPIRED,
                    () -> driverService.register(request, BRANCH_ID));

            verifyNoInteractions(transactionTemplate, driverRepository, identityClient, outboxService);
        }

        @Test
        void duplicatedLicense_throwsConflictWithoutCreatingAccount() {
            runTransactionsInline();
            when(driverRepository.existsByLicenseNumberIgnoreCase(LICENSE)).thenReturn(true);
            CreateDriverRequest request = createRequest(EXPIRES_ON);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.LICENSE_ALREADY_EXISTS,
                    () -> driverService.register(request, BRANCH_ID));

            verify(driverRepository, never()).save(any());
            verifyNoInteractions(identityClient, licenseAlertRepository, outboxService);
        }

        @Test
        void inactiveBranch_throwsWithoutCreatingAccount() {
            runTransactionsInline();
            doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE, "Sucursal inactiva"))
                    .when(branchProjectionService).assertActive(BRANCH_ID);
            CreateDriverRequest request = createRequest(EXPIRES_ON);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE,
                    () -> driverService.register(request, BRANCH_ID));

            verify(driverRepository, never()).save(any());
            verifyNoInteractions(identityClient, outboxService);
        }

        @Test
        void identityFailure_removesLocalDriverAndRethrows() {
            runTransactionsInline();
            runCompensationsInline();
            persistInMemory();
            ApiException failure = new ApiException(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS,
                    "El correo ya está registrado");
            when(identityClient.createDriverUser(any())).thenThrow(failure);
            CreateDriverRequest request = createRequest(EXPIRES_ON);

            ApiException thrown = assertThrows(ApiException.class, () -> driverService.register(request, BRANCH_ID));

            assertSame(failure, thrown);
            verify(licenseAlertRepository).deleteByDriverId(DRIVER_ID);
            verify(driverRepository).deleteById(DRIVER_ID);
            verifyNoInteractions(outboxService);
        }

        @Test
        void driverMissingAfterAccountCreated_throwsInternalErrorWithoutCompensating() {
            runTransactionsInline();
            persistInMemory();
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            when(identityClient.createDriverUser(any())).thenReturn(account());
            CreateDriverRequest request = createRequest(EXPIRES_ON);

            assertApiError(HttpStatus.INTERNAL_SERVER_ERROR, null,
                    () -> driverService.register(request, BRANCH_ID));

            verify(driverRepository, never()).deleteById(any());
            verify(licenseAlertRepository, never()).deleteByDriverId(any());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Update {

        @Test
        void sameExpiration_updatesDriverWithoutTouchingAlerts() {
            DriverEntity driver = driver(true);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

            DriverResponse response = driverService.update(DRIVER_ID, updateRequest(EXPIRES_ON), BRANCH_ID);

            assertEquals(NEW_LICENSE, driver.getLicenseNumber());
            assertEquals(LicenseType.B, driver.getLicenseType());
            assertEquals("55559999", driver.getPhone());
            assertEquals(NEW_SALARY, driver.getBaseSalaryPerTrip());
            assertEquals(FULL_NAME, driver.getFullName());
            assertEquals(EMAIL, driver.getEmail());
            verify(driverRepository).existsByLicenseNumberIgnoreCaseAndIdNot(NEW_LICENSE, DRIVER_ID);
            verifyNoInteractions(licenseAlertRepository);
            verify(outboxService).record(EventTypes.AGGREGATE_DRIVER, DRIVER_ID, EventTypes.DRIVER_UPDATED,
                    DriverEventPayload.fromEntity(driver));
            assertEquals(NEW_LICENSE, response.getLicenseNumber());
        }

        @Test
        void newExpiration_replacesPendingAlerts() {
            DriverEntity driver = driver(true);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            LocalDate newExpiration = TODAY.plusDays(20);

            driverService.update(DRIVER_ID, updateRequest(newExpiration), BRANCH_ID);

            assertEquals(newExpiration, driver.getLicenseExpiresOn());
            verify(licenseAlertRepository).deletePendingByDriverId(DRIVER_ID);
            verify(licenseAlertRepository).saveAll(alertsCaptor.capture());
            assertEquals(List.of(15, 3),
                    alertsCaptor.getValue().stream().map(alert -> alert.getDaysBefore().intValue()).toList());
        }

        @Test
        void unchangedExpiredLicense_isAllowed() {
            DriverEntity driver = driver(true);
            LocalDate expired = TODAY.minusDays(5);
            driver.setLicenseExpiresOn(expired);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

            driverService.update(DRIVER_ID, updateRequest(expired), BRANCH_ID);

            assertEquals(NEW_LICENSE, driver.getLicenseNumber());
            verifyNoInteractions(licenseAlertRepository);
        }

        @Test
        void newExpirationInThePast_throwsLicenseExpired() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));
            UpdateDriverRequest request = updateRequest(TODAY.minusDays(1));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.LICENSE_EXPIRED,
                    () -> driverService.update(DRIVER_ID, request, BRANCH_ID));

            verifyNoInteractions(licenseAlertRepository, outboxService);
        }

        @Test
        void duplicatedLicense_throwsConflict() {
            DriverEntity driver = driver(true);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            when(driverRepository.existsByLicenseNumberIgnoreCaseAndIdNot(NEW_LICENSE, DRIVER_ID)).thenReturn(true);
            UpdateDriverRequest request = updateRequest(EXPIRES_ON);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.LICENSE_ALREADY_EXISTS,
                    () -> driverService.update(DRIVER_ID, request, BRANCH_ID));

            assertEquals(LICENSE, driver.getLicenseNumber());
            verifyNoInteractions(licenseAlertRepository, outboxService);
        }

        @Test
        void inactiveDriver_throwsConflict() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(false)));
            UpdateDriverRequest request = updateRequest(EXPIRES_ON);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.DRIVER_INACTIVE,
                    () -> driverService.update(DRIVER_ID, request, BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void driverFromOtherBranch_throwsForbidden() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));
            UpdateDriverRequest request = updateRequest(EXPIRES_ON);

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.DRIVER_NOT_IN_BRANCH,
                    () -> driverService.update(DRIVER_ID, request, OTHER_BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void unknownDriver_throwsNotFound() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            UpdateDriverRequest request = updateRequest(EXPIRES_ON);

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.DRIVER_NOT_FOUND,
                    () -> driverService.update(DRIVER_ID, request, BRANCH_ID));
        }
    }

    @Nested
    class Deactivate {

        @Test
        void driverWithoutActiveTrips_isDeactivatedAndRecordsEvent() {
            DriverEntity driver = driver(true);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            when(tripClient.countActiveTripsByDriver(DRIVER_ID)).thenReturn(0L);
            runTransactionsInline();

            DriverResponse response = driverService.deactivate(DRIVER_ID, BRANCH_ID);

            assertFalse(driver.isActive());
            assertFalse(response.isActive());
            verify(outboxService).record(EventTypes.AGGREGATE_DRIVER, DRIVER_ID, EventTypes.DRIVER_DEACTIVATED,
                    DriverEventPayload.fromEntity(driver));
        }

        @Test
        void alreadyInactiveDriver_throwsDriverInactive() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(false)));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DRIVER_INACTIVE,
                    () -> driverService.deactivate(DRIVER_ID, BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }

        @Test
        void driverWithActiveTrips_throwsHasActiveTrips() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));
            when(tripClient.countActiveTripsByDriver(DRIVER_ID)).thenReturn(1L);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DRIVER_HAS_ACTIVE_TRIPS,
                    () -> driverService.deactivate(DRIVER_ID, BRANCH_ID));

            verifyNoInteractions(transactionTemplate, outboxService);
        }

        @Test
        void driverFromOtherBranch_throwsForbidden() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.DRIVER_NOT_IN_BRANCH,
                    () -> driverService.deactivate(DRIVER_ID, OTHER_BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }
    }

    @Nested
    class Search {

        @Test
        void withoutExpirationFilter_sortsByNewestAndClampsPaging() {
            when(driverRepository.findAll(ArgumentMatchers.<Specification<DriverEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(driver(true)), PageRequest.of(0, 100), 1));

            PageResponse<DriverResponse> result = driverService.search(true, null, BRANCH_ID, -3, 1000);

            verify(driverRepository).findAll(ArgumentMatchers.<Specification<DriverEntity>>any(),
                    pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(100, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
            assertEquals(1, result.getContent().size());
            assertEquals(DRIVER_ID, result.getContent().getFirst().getId());
        }

        @Test
        void withExpirationFilter_sortsBySoonestExpiration() {
            when(driverRepository.findAll(ArgumentMatchers.<Specification<DriverEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            driverService.search(null, 30, null, 0, 20);

            verify(driverRepository).findAll(ArgumentMatchers.<Specification<DriverEntity>>any(),
                    pageableCaptor.capture());
            assertEquals(Sort.by(Sort.Direction.ASC, "licenseExpiresOn"), pageableCaptor.getValue().getSort());
            assertEquals(20, pageableCaptor.getValue().getPageSize());
        }

        @Test
        void negativeExpirationDays_throwsValidationError() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    () -> driverService.search(null, -1, BRANCH_ID, 0, 20));

            verifyNoInteractions(driverRepository);
        }
    }

    @Nested
    class FindById {

        @Test
        void driverWithPhoto_returnsPresignedUrl() {
            DriverEntity driver = driver(true);
            driver.setPhotoUrl(OLD_PHOTO_KEY);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            when(photoStorageService.presignedUrl(OLD_PHOTO_KEY)).thenReturn(PRESIGNED_URL);

            DriverResponse response = driverService.findById(DRIVER_ID, BRANCH_ID);

            assertEquals(DRIVER_ID, response.getId());
            assertEquals(PRESIGNED_URL, response.getPhotoUrl());
        }

        @Test
        void withoutBranchScope_returnsDriverFromAnyBranch() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));

            DriverResponse response = driverService.findById(DRIVER_ID, null);

            assertEquals(BRANCH_ID, response.getBranchId());
        }

        @Test
        void driverFromOtherBranch_throwsForbidden() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.DRIVER_NOT_IN_BRANCH,
                    () -> driverService.findById(DRIVER_ID, OTHER_BRANCH_ID));
        }

        @Test
        void unknownDriver_throwsNotFound() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.DRIVER_NOT_FOUND,
                    () -> driverService.findById(DRIVER_ID, null));
        }
    }

    @Nested
    class FindByUserId {

        @Test
        void linkedUser_returnsDriver() {
            when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.of(driver(true)));

            DriverResponse response = driverService.findByUserId(USER_ID);

            assertEquals(DRIVER_ID, response.getId());
            assertEquals(USER_ID, response.getUserId());
        }

        @Test
        void unlinkedUser_throwsNotFound() {
            when(driverRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.DRIVER_NOT_FOUND,
                    () -> driverService.findByUserId(USER_ID));
        }
    }

    @Nested
    class UploadPhoto {

        private final MockMultipartFile photo =
                new MockMultipartFile("file", "driver.jpg", "image/jpeg", new byte[]{1, 2, 3});

        @Test
        void validPhoto_replacesKeyAndDeletesPreviousOne() {
            DriverEntity driver = driver(true);
            driver.setPhotoUrl(OLD_PHOTO_KEY);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            when(photoStorageService.upload(PhotoOwner.DRIVER, DRIVER_ID, photo)).thenReturn(NEW_PHOTO_KEY);
            when(photoStorageService.presignedUrl(NEW_PHOTO_KEY)).thenReturn(PRESIGNED_URL);
            runTransactionsInline();

            DriverResponse response = driverService.uploadPhoto(DRIVER_ID, photo, BRANCH_ID);

            assertEquals(NEW_PHOTO_KEY, driver.getPhotoUrl());
            assertEquals(PRESIGNED_URL, response.getPhotoUrl());
            verify(photoStorageService).delete(OLD_PHOTO_KEY);
            verify(photoStorageService, never()).delete(NEW_PHOTO_KEY);
        }

        @Test
        void failedTransaction_deletesUploadedPhotoAndRethrows() {
            DriverEntity driver = driver(true);
            driver.setPhotoUrl(OLD_PHOTO_KEY);
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
            when(photoStorageService.upload(PhotoOwner.DRIVER, DRIVER_ID, photo)).thenReturn(NEW_PHOTO_KEY);
            IllegalStateException failure = new IllegalStateException("BD no disponible");
            when(transactionTemplate.execute(any())).thenThrow(failure);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> driverService.uploadPhoto(DRIVER_ID, photo, BRANCH_ID));

            assertSame(failure, thrown);
            verify(photoStorageService).delete(NEW_PHOTO_KEY);
            verify(photoStorageService, never()).delete(OLD_PHOTO_KEY);
        }

        @Test
        void inactiveDriver_throwsConflictWithoutUploading() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(false)));

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.DRIVER_INACTIVE,
                    () -> driverService.uploadPhoto(DRIVER_ID, photo, BRANCH_ID));

            verifyNoInteractions(photoStorageService, transactionTemplate);
        }

        @Test
        void driverFromOtherBranch_throwsForbiddenWithoutUploading() {
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(true)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.DRIVER_NOT_IN_BRANCH,
                    () -> driverService.uploadPhoto(DRIVER_ID, photo, OTHER_BRANCH_ID));

            verifyNoInteractions(photoStorageService, transactionTemplate);
        }
    }

    static Stream<Arguments> alertsByDaysToExpire() {
        return Stream.of(
                Arguments.of(60, List.of(30, 15, 3)),
                Arguments.of(20, List.of(15, 3)),
                Arguments.of(10, List.of(3)),
                Arguments.of(3, List.of()),
                Arguments.of(0, List.of()));
    }

    private static DriverEntity driver(boolean active) {
        return DriverEntity.builder()
                .id(DRIVER_ID)
                .userId(USER_ID)
                .fullName(FULL_NAME)
                .licenseNumber(LICENSE)
                .licenseType(LicenseType.A)
                .licenseExpiresOn(EXPIRES_ON)
                .phone(PHONE)
                .email(EMAIL)
                .baseSalaryPerTrip(SALARY)
                .branchId(BRANCH_ID)
                .active(active)
                .build();
    }

    private static CreateDriverRequest createRequest(LocalDate expiresOn) {
        return new CreateDriverRequest(" Juan Pérez ", " lic-001 ", LicenseType.A, expiresOn, " 55551234 ",
                " Juan@Mail.COM ", SALARY);
    }

    private static UpdateDriverRequest updateRequest(LocalDate expiresOn) {
        return new UpdateDriverRequest(" lic-002 ", LicenseType.B, expiresOn, " 55559999 ", NEW_SALARY);
    }

    private static IdentityUserResponse account() {
        return new IdentityUserResponse(USER_ID, EMAIL, FULL_NAME, "DRIVER");
    }

    private void persistInMemory() {
        when(driverRepository.save(any(DriverEntity.class))).thenAnswer(invocation -> {
            persisted = invocation.getArgument(0);
            persisted.setId(DRIVER_ID);
            return persisted;
        });
    }

    private void runTransactionsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private void runCompensationsInline() {
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
