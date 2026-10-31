package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.client.TripClient;
import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.domain.ProcessedEventEntity;
import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateBusRequest;
import com.urbantransport.fleet.event.BusEventPayload;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BusRepository;
import com.urbantransport.fleet.repository.ProcessedEventRepository;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.service.PhotoStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID BUS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final String PLATE = "P-123ABC";
    private static final String NEW_PLATE = "P-999XYZ";
    private static final String BRAND = "Volvo";
    private static final String MODEL = "B8R";
    private static final int YEAR = 2020;
    private static final int ROWS = 10;
    private static final int COLUMNS = 4;
    private static final int CAPACITY = ROWS * COLUMNS;
    private static final int AISLE = 2;
    private static final BigDecimal ODOMETER = new BigDecimal("1000.00");

    private static final String OLD_PHOTO_KEY = "buses/old.jpg";
    private static final String NEW_PHOTO_KEY = "buses/new.jpg";
    private static final String PRESIGNED_URL = "https://s3.amazonaws.com/presigned";

    @Mock
    private BusRepository busRepository;

    @Mock
    private BranchProjectionService branchProjectionService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private PhotoStorageService photoStorageService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TripClient tripClient;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<BusEntity> busCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private BusServiceImpl busService;

    @BeforeEach
    void setUp() {
        busService = new BusServiceImpl(busRepository, branchProjectionService, outboxService,
                photoStorageService, transactionTemplate, tripClient, processedEventRepository);
        ReflectionTestUtils.setField(busService, "zone", ZONE);
    }

    @Nested
    class Create {

        @Test
        void validRequest_savesNormalizedBusAndRecordsEvent() {
            when(busRepository.save(any(BusEntity.class))).thenAnswer(savedWithId());
            CreateBusRequest request = new CreateBusRequest(" p-123abc ", " Volvo ", " B8R ", YEAR, CAPACITY,
                    ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE, ODOMETER);

            BusResponse response = busService.create(request, BRANCH_ID);

            verify(branchProjectionService).assertActive(BRANCH_ID);
            verify(busRepository).save(busCaptor.capture());
            BusEntity saved = busCaptor.getValue();
            assertEquals(PLATE, saved.getPlate());
            assertEquals(BRAND, saved.getBrand());
            assertEquals(MODEL, saved.getModel());
            assertEquals(YEAR, saved.getManufactureYear().intValue());
            assertEquals(CAPACITY, saved.getCapacity());
            assertEquals(ROWS, saved.getSeatRows().intValue());
            assertEquals(COLUMNS, saved.getSeatColumns().intValue());
            assertEquals(AISLE, saved.getAisleAfterColumn().intValue());
            assertEquals(BusStatus.DISPONIBLE, saved.getStatus());
            assertEquals(ODOMETER, saved.getOdometerKm());
            assertEquals(BRANCH_ID, saved.getBranchId());
            verify(outboxService).record(EventTypes.AGGREGATE_BUS, BUS_ID, EventTypes.BUS_REGISTERED,
                    BusEventPayload.fromEntity(saved));
            assertEquals(BUS_ID, response.getId());
            assertEquals(PLATE, response.getPlate());
            assertNull(response.getPhotoUrl());
        }

        @Test
        void withoutOdometerAndAisle_startsAtZeroWithoutAisle() {
            when(busRepository.save(any(BusEntity.class))).thenAnswer(savedWithId());

            busService.create(createRequest(YEAR, CAPACITY, ROWS, COLUMNS, null, BusStatus.EN_TALLER), BRANCH_ID);

            verify(busRepository).save(busCaptor.capture());
            assertEquals(BigDecimal.ZERO, busCaptor.getValue().getOdometerKm());
            assertNull(busCaptor.getValue().getAisleAfterColumn());
            assertEquals(BusStatus.EN_TALLER, busCaptor.getValue().getStatus());
        }

        @Test
        void zeroCapacity_throwsInvalidCapacity() {
            CreateBusRequest request = createRequest(YEAR, 0, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CAPACITY,
                    () -> busService.create(request, BRANCH_ID));

            verifyNoInteractions(busRepository, outboxService);
        }

        @Test
        void capacityDifferentFromSeats_throwsInvalidCapacity() {
            CreateBusRequest request = createRequest(YEAR, CAPACITY + 1, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CAPACITY,
                    () -> busService.create(request, BRANCH_ID));

            verifyNoInteractions(busRepository, outboxService);
        }

        @Test
        void aisleAfterLastColumn_throwsInvalidSeatLayout() {
            CreateBusRequest request = createRequest(YEAR, CAPACITY, ROWS, COLUMNS, COLUMNS, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_SEAT_LAYOUT,
                    () -> busService.create(request, BRANCH_ID));

            verifyNoInteractions(busRepository, outboxService);
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.fleet.service.impl.BusServiceImplTest#yearsOutOfRange")
        void yearOutOfRange_throwsInvalidYear(int year) {
            CreateBusRequest request = createRequest(year, CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_YEAR,
                    () -> busService.create(request, BRANCH_ID));

            verifyNoInteractions(busRepository, outboxService);
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.fleet.service.impl.BusServiceImplTest#yearsAtLimits")
        void yearAtLimit_isAccepted(int year) {
            when(busRepository.save(any(BusEntity.class))).thenAnswer(savedWithId());

            BusResponse response = busService.create(
                    createRequest(year, CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE), BRANCH_ID);

            assertEquals(year, response.getManufactureYear());
        }

        @Test
        void statusInTransit_throwsInvalidStatus() {
            CreateBusRequest request = createRequest(YEAR, CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.EN_TRANSITO);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_STATUS,
                    () -> busService.create(request, BRANCH_ID));

            verifyNoInteractions(busRepository, outboxService);
        }

        @Test
        void duplicatedPlate_throwsConflict() {
            when(busRepository.existsByPlateIgnoreCase(PLATE)).thenReturn(true);
            CreateBusRequest request = createRequest(YEAR, CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.PLATE_ALREADY_EXISTS,
                    () -> busService.create(request, BRANCH_ID));

            verify(busRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }

        @Test
        void inactiveBranch_propagatesErrorWithoutSaving() {
            doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE, "Sucursal inactiva"))
                    .when(branchProjectionService).assertActive(BRANCH_ID);
            CreateBusRequest request = createRequest(YEAR, CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE,
                    () -> busService.create(request, BRANCH_ID));

            verify(busRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Update {

        @Test
        void sameSeatLayout_updatesBusWithoutAskingTrip() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            runTransactionsInline();

            BusResponse response = busService.update(BUS_ID,
                    updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.EN_TALLER), BRANCH_ID);

            assertEquals(NEW_PLATE, bus.getPlate());
            assertEquals("Mercedes", bus.getBrand());
            assertEquals("O500", bus.getModel());
            assertEquals(2021, bus.getManufactureYear().intValue());
            assertEquals(BusStatus.EN_TALLER, bus.getStatus());
            verify(busRepository).existsByPlateIgnoreCaseAndIdNot(NEW_PLATE, BUS_ID);
            verify(outboxService).record(EventTypes.AGGREGATE_BUS, BUS_ID, EventTypes.BUS_UPDATED,
                    BusEventPayload.fromEntity(bus));
            verifyNoInteractions(tripClient);
            assertEquals(NEW_PLATE, response.getPlate());
        }

        @Test
        void seatLayoutChangeWithoutActiveTrips_updatesLayout() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            when(tripClient.countActiveTripsByBus(BUS_ID)).thenReturn(0L);
            runTransactionsInline();

            busService.update(BUS_ID, updateRequest(48, 12, COLUMNS, null, null), BRANCH_ID);

            assertEquals(48, bus.getCapacity());
            assertEquals(12, bus.getSeatRows().intValue());
            assertNull(bus.getAisleAfterColumn());
        }

        @Test
        void seatLayoutChangeWithActiveTrips_throwsSeatLayoutLocked() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));
            when(tripClient.countActiveTripsByBus(BUS_ID)).thenReturn(1L);
            UpdateBusRequest request = updateRequest(48, 12, COLUMNS, AISLE, null);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.SEAT_LAYOUT_LOCKED,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));

            verifyNoInteractions(transactionTemplate, outboxService);
        }

        @Test
        void unknownBus_throwsNotFound() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.empty());
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, null);

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));
        }

        @Test
        void busFromOtherBranch_throwsForbidden() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, null);

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    () -> busService.update(BUS_ID, request, OTHER_BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }

        @Test
        void inactiveBus_throwsConflict() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.INACTIVO)));
            runTransactionsInline();
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, null);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BUS_INACTIVE,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void plateOfAnotherBus_throwsConflict() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));
            when(busRepository.existsByPlateIgnoreCaseAndIdNot(NEW_PLATE, BUS_ID)).thenReturn(true);
            runTransactionsInline();
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, null);

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.PLATE_ALREADY_EXISTS,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void statusInTransit_throwsInvalidStatus() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));
            runTransactionsInline();
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.EN_TRANSITO);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_STATUS,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void statusChangeOfBusInTransit_throwsBusInTransit() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.EN_TRANSITO)));
            runTransactionsInline();
            UpdateBusRequest request = updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, BusStatus.DISPONIBLE);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_IN_TRANSIT,
                    () -> busService.update(BUS_ID, request, BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void withoutStatus_keepsCurrentStatus() {
            BusEntity bus = bus(BusStatus.EN_TRANSITO);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            runTransactionsInline();

            busService.update(BUS_ID, updateRequest(CAPACITY, ROWS, COLUMNS, AISLE, null), BRANCH_ID);

            assertEquals(BusStatus.EN_TRANSITO, bus.getStatus());
            assertEquals(NEW_PLATE, bus.getPlate());
        }
    }

    @Nested
    class Search {

        @Test
        void outOfRangePaging_isClampedAndSortedByNewest() {
            when(busRepository.findAll(ArgumentMatchers.<Specification<BusEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(bus(BusStatus.DISPONIBLE)), PageRequest.of(0, 100), 1));

            PageResponse<BusResponse> result = busService.search(BusStatus.DISPONIBLE, BRANCH_ID, -1, 500);

            verify(busRepository).findAll(ArgumentMatchers.<Specification<BusEntity>>any(), pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(100, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
            assertEquals(1, result.getContent().size());
            assertEquals(BUS_ID, result.getContent().getFirst().getId());
            assertEquals(1, result.getTotalElements());
        }

        @Test
        void zeroSize_usesSizeOne() {
            when(busRepository.findAll(ArgumentMatchers.<Specification<BusEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 1), 0));

            busService.search(null, null, 2, 0);

            verify(busRepository).findAll(ArgumentMatchers.<Specification<BusEntity>>any(), pageableCaptor.capture());
            assertEquals(2, pageableCaptor.getValue().getPageNumber());
            assertEquals(1, pageableCaptor.getValue().getPageSize());
        }
    }

    @Nested
    class FindById {

        @Test
        void busWithPhoto_returnsPresignedUrl() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            bus.setPhotoUrl(OLD_PHOTO_KEY);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            when(photoStorageService.presignedUrl(OLD_PHOTO_KEY)).thenReturn(PRESIGNED_URL);

            BusResponse response = busService.findById(BUS_ID, BRANCH_ID);

            assertEquals(BUS_ID, response.getId());
            assertEquals(PRESIGNED_URL, response.getPhotoUrl());
        }

        @Test
        void withoutBranchScope_returnsBusFromAnyBranch() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));

            BusResponse response = busService.findById(BUS_ID, null);

            assertEquals(BRANCH_ID, response.getBranchId());
        }

        @Test
        void busFromOtherBranch_throwsForbidden() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    () -> busService.findById(BUS_ID, OTHER_BRANCH_ID));
        }

        @Test
        void unknownBus_throwsNotFound() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND,
                    () -> busService.findById(BUS_ID, BRANCH_ID));
        }
    }

    @Nested
    class Deactivate {

        @Test
        void busWithoutActiveTrips_isDeactivatedAndRecordsEvent() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            when(tripClient.countActiveTripsByBus(BUS_ID)).thenReturn(0L);
            runTransactionsInline();

            BusResponse response = busService.deactivate(BUS_ID, BRANCH_ID);

            assertEquals(BusStatus.INACTIVO, bus.getStatus());
            assertEquals(BusStatus.INACTIVO, response.getStatus());
            verify(outboxService).record(EventTypes.AGGREGATE_BUS, BUS_ID, EventTypes.BUS_DEACTIVATED,
                    BusEventPayload.fromEntity(bus));
        }

        @Test
        void alreadyInactiveBus_throwsBusInactive() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.INACTIVO)));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_INACTIVE,
                    () -> busService.deactivate(BUS_ID, BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }

        @Test
        void busInTransit_throwsHasActiveTrips() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.EN_TRANSITO)));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_HAS_ACTIVE_TRIPS,
                    () -> busService.deactivate(BUS_ID, BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }

        @Test
        void busWithScheduledTrips_throwsHasActiveTrips() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));
            when(tripClient.countActiveTripsByBus(BUS_ID)).thenReturn(2L);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_HAS_ACTIVE_TRIPS,
                    () -> busService.deactivate(BUS_ID, BRANCH_ID));

            verifyNoInteractions(transactionTemplate, outboxService);
        }

        @Test
        void busFromOtherBranch_throwsForbidden() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    () -> busService.deactivate(BUS_ID, OTHER_BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }
    }

    @Nested
    class UploadPhoto {

        private final MockMultipartFile photo =
                new MockMultipartFile("file", "bus.jpg", "image/jpeg", new byte[]{1, 2, 3});

        @Test
        void validPhoto_replacesKeyAndDeletesPreviousOne() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            bus.setPhotoUrl(OLD_PHOTO_KEY);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            when(photoStorageService.upload(PhotoOwner.BUS, BUS_ID, photo)).thenReturn(NEW_PHOTO_KEY);
            when(photoStorageService.presignedUrl(NEW_PHOTO_KEY)).thenReturn(PRESIGNED_URL);
            runTransactionsInline();

            BusResponse response = busService.uploadPhoto(BUS_ID, photo, BRANCH_ID);

            assertEquals(NEW_PHOTO_KEY, bus.getPhotoUrl());
            assertEquals(PRESIGNED_URL, response.getPhotoUrl());
            verify(photoStorageService).delete(OLD_PHOTO_KEY);
            verify(photoStorageService, never()).delete(NEW_PHOTO_KEY);
        }

        @Test
        void failedTransaction_deletesUploadedPhotoAndRethrows() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            bus.setPhotoUrl(OLD_PHOTO_KEY);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            when(photoStorageService.upload(PhotoOwner.BUS, BUS_ID, photo)).thenReturn(NEW_PHOTO_KEY);
            IllegalStateException failure = new IllegalStateException("BD no disponible");
            when(transactionTemplate.execute(any())).thenThrow(failure);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> busService.uploadPhoto(BUS_ID, photo, BRANCH_ID));

            assertSame(failure, thrown);
            verify(photoStorageService).delete(NEW_PHOTO_KEY);
            verify(photoStorageService, never()).delete(OLD_PHOTO_KEY);
        }

        @Test
        void inactiveBus_throwsConflictWithoutUploading() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.INACTIVO)));

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BUS_INACTIVE,
                    () -> busService.uploadPhoto(BUS_ID, photo, BRANCH_ID));

            verifyNoInteractions(photoStorageService, transactionTemplate);
        }

        @Test
        void busFromOtherBranch_throwsForbiddenWithoutUploading() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus(BusStatus.DISPONIBLE)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    () -> busService.uploadPhoto(BUS_ID, photo, OTHER_BRANCH_ID));

            verifyNoInteractions(photoStorageService, transactionTemplate);
        }
    }

    @Nested
    class ApplyTripStarted {

        @Test
        void availableBus_isMarkedInTransit() {
            BusEntity bus = bus(BusStatus.DISPONIBLE);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));

            busService.applyTripStarted(EVENT_ID, EventTypes.TRIP_STARTED, BUS_ID);

            assertEquals(BusStatus.EN_TRANSITO, bus.getStatus());
            verify(outboxService).record(EventTypes.AGGREGATE_BUS, BUS_ID, EventTypes.BUS_UPDATED,
                    BusEventPayload.fromEntity(bus));
            assertMarkedProcessed(EventTypes.TRIP_STARTED);
        }

        @Test
        void inactiveBus_keepsStatusButMarksEvent() {
            BusEntity bus = bus(BusStatus.INACTIVO);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));

            busService.applyTripStarted(EVENT_ID, EventTypes.TRIP_STARTED, BUS_ID);

            assertEquals(BusStatus.INACTIVO, bus.getStatus());
            verifyNoInteractions(outboxService);
            assertMarkedProcessed(EventTypes.TRIP_STARTED);
        }

        @Test
        void unknownBus_onlyMarksEvent() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            busService.applyTripStarted(EVENT_ID, EventTypes.TRIP_STARTED, BUS_ID);

            verifyNoInteractions(outboxService);
            assertMarkedProcessed(EventTypes.TRIP_STARTED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            busService.applyTripStarted(EVENT_ID, EventTypes.TRIP_STARTED, BUS_ID);

            verifyNoInteractions(busRepository, outboxService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyTripCompleted {

        @Test
        void busInTransit_updatesOdometerAndBecomesAvailable() {
            BusEntity bus = bus(BusStatus.EN_TRANSITO);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));
            BigDecimal odometerEnd = new BigDecimal("1250.75");

            busService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, odometerEnd);

            assertEquals(odometerEnd, bus.getOdometerKm());
            assertEquals(BusStatus.DISPONIBLE, bus.getStatus());
            verify(outboxService).record(EventTypes.AGGREGATE_BUS, BUS_ID, EventTypes.BUS_UPDATED,
                    BusEventPayload.fromEntity(bus));
            assertMarkedProcessed(EventTypes.TRIP_COMPLETED);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"999.99", "1000.00"})
        void odometerNotGreater_keepsCurrentOdometer(String odometerEnd) {
            BusEntity bus = bus(BusStatus.EN_TALLER);
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.of(bus));

            busService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID,
                    odometerEnd == null ? null : new BigDecimal(odometerEnd));

            assertEquals(ODOMETER, bus.getOdometerKm());
            assertEquals(BusStatus.EN_TALLER, bus.getStatus());
            assertMarkedProcessed(EventTypes.TRIP_COMPLETED);
        }

        @Test
        void unknownBus_onlyMarksEvent() {
            when(busRepository.findById(BUS_ID)).thenReturn(Optional.empty());

            busService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, ODOMETER);

            verifyNoInteractions(outboxService);
            assertMarkedProcessed(EventTypes.TRIP_COMPLETED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            busService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, ODOMETER);

            verifyNoInteractions(busRepository, outboxService);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static IntStream yearsOutOfRange() {
        return IntStream.of(1949, Year.now(ZONE).getValue() + 2);
    }

    static IntStream yearsAtLimits() {
        return IntStream.of(1950, Year.now(ZONE).getValue() + 1);
    }

    private static BusEntity bus(BusStatus status) {
        return BusEntity.builder()
                .id(BUS_ID)
                .plate(PLATE)
                .brand(BRAND)
                .model(MODEL)
                .manufactureYear((short) YEAR)
                .capacity(CAPACITY)
                .seatRows((short) ROWS)
                .seatColumns((short) COLUMNS)
                .aisleAfterColumn((short) AISLE)
                .status(status)
                .odometerKm(ODOMETER)
                .branchId(BRANCH_ID)
                .build();
    }

    private static CreateBusRequest createRequest(int year, int capacity, int rows, int columns, Integer aisle,
                                                  BusStatus status) {
        return new CreateBusRequest(PLATE, BRAND, MODEL, year, capacity, rows, columns, aisle, status, null);
    }

    private static UpdateBusRequest updateRequest(int capacity, int rows, int columns, Integer aisle,
                                                  BusStatus status) {
        return new UpdateBusRequest(" p-999xyz ", " Mercedes ", " O500 ", 2021, capacity, rows, columns, aisle,
                status);
    }

    private static Answer<BusEntity> savedWithId() {
        return invocation -> {
            BusEntity bus = invocation.getArgument(0);
            bus.setId(BUS_ID);
            return bus;
        };
    }

    private void runTransactionsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
