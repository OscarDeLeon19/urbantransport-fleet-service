package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.client.TripClient;
import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.RouteEntity;
import com.urbantransport.fleet.dto.CreateRouteRequest;
import com.urbantransport.fleet.dto.InternalRouteResponse;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.RouteResponse;
import com.urbantransport.fleet.dto.UpdateRouteRequest;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.event.RouteEventPayload;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.RouteRepository;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class RouteServiceImplTest {

    private static final UUID ROUTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORIGIN_BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESTINATION_BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final BigDecimal DISTANCE = new BigDecimal("120.50");
    private static final BigDecimal PRICE = new BigDecimal("45.00");
    private static final BigDecimal NEW_DISTANCE = new BigDecimal("130.00");
    private static final BigDecimal NEW_PRICE = new BigDecimal("50.00");
    private static final String ORIGIN_NAME = "Central";
    private static final String DESTINATION_NAME = "Xela";

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private BranchProjectionRepository branchProjectionRepository;

    @Mock
    private BranchProjectionService branchProjectionService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private TripClient tripClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Captor
    private ArgumentCaptor<RouteEntity> routeCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private RouteServiceImpl routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteServiceImpl(routeRepository, branchProjectionRepository, branchProjectionService,
                outboxService, tripClient, transactionTemplate);
    }

    @Nested
    class Create {

        @Test
        void validRequest_savesRouteAndRecordsEvent() {
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID))
                    .thenReturn(Optional.of(branch(DESTINATION_BRANCH_ID, DESTINATION_NAME, true)));
            when(routeRepository.save(any(RouteEntity.class))).thenAnswer(invocation -> {
                RouteEntity route = invocation.getArgument(0);
                route.setId(ROUTE_ID);
                return route;
            });

            RouteResponse response = routeService.create(createRequest(), ORIGIN_BRANCH_ID, USER_ID);

            verify(branchProjectionService).assertActive(ORIGIN_BRANCH_ID);
            verify(routeRepository).save(routeCaptor.capture());
            RouteEntity saved = routeCaptor.getValue();
            assertEquals(ORIGIN_BRANCH_ID, saved.getOriginBranchId());
            assertEquals(DESTINATION_BRANCH_ID, saved.getDestinationBranchId());
            assertEquals(DISTANCE, saved.getDistanceKm());
            assertEquals(PRICE, saved.getTicketPrice());
            assertEquals(USER_ID, saved.getCreatedBy());
            assertFalse(saved.isDeleted());
            verify(outboxService).record(EventTypes.AGGREGATE_ROUTE, ROUTE_ID, EventTypes.ROUTE_CREATED,
                    RouteEventPayload.fromEntity(saved));
            assertEquals(ROUTE_ID, response.getId());
            assertEquals(DESTINATION_BRANCH_ID, response.getDestinationBranchId());
        }

        @Test
        void sameOriginAndDestination_throwsBadRequest() {
            CreateRouteRequest request = new CreateRouteRequest(ORIGIN_BRANCH_ID, DISTANCE, PRICE);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.SAME_ORIGIN_DESTINATION,
                    () -> routeService.create(request, ORIGIN_BRANCH_ID, USER_ID));

            verifyNoInteractions(branchProjectionService, branchProjectionRepository, routeRepository, outboxService);
        }

        @Test
        void inactiveOriginBranch_propagatesErrorWithoutSaving() {
            doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE, "Sucursal inactiva"))
                    .when(branchProjectionService).assertActive(ORIGIN_BRANCH_ID);
            CreateRouteRequest request = createRequest();

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE,
                    () -> routeService.create(request, ORIGIN_BRANCH_ID, USER_ID));

            verifyNoInteractions(routeRepository, outboxService);
        }

        @Test
        void unknownDestination_throwsDestinationInvalid() {
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID)).thenReturn(Optional.empty());
            CreateRouteRequest request = createRequest();

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DESTINATION_BRANCH_INVALID,
                    () -> routeService.create(request, ORIGIN_BRANCH_ID, USER_ID));

            verifyNoInteractions(routeRepository, outboxService);
        }

        @Test
        void inactiveDestination_throwsDestinationInvalid() {
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID))
                    .thenReturn(Optional.of(branch(DESTINATION_BRANCH_ID, DESTINATION_NAME, false)));
            CreateRouteRequest request = createRequest();

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DESTINATION_BRANCH_INVALID,
                    () -> routeService.create(request, ORIGIN_BRANCH_ID, USER_ID));

            verifyNoInteractions(routeRepository, outboxService);
        }

        @Test
        void existingRouteToDestination_throwsConflict() {
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID))
                    .thenReturn(Optional.of(branch(DESTINATION_BRANCH_ID, DESTINATION_NAME, true)));
            when(routeRepository.existsByOriginBranchIdAndDestinationBranchIdAndDeletedFalse(ORIGIN_BRANCH_ID,
                    DESTINATION_BRANCH_ID)).thenReturn(true);
            CreateRouteRequest request = createRequest();

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.ROUTE_ALREADY_EXISTS,
                    () -> routeService.create(request, ORIGIN_BRANCH_ID, USER_ID));

            verify(routeRepository, never()).save(any());
            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Update {

        @Test
        void ownRoute_updatesDistanceAndPriceAndRecordsEvent() {
            RouteEntity route = route(false);
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

            RouteResponse response = routeService.update(ROUTE_ID, new UpdateRouteRequest(NEW_DISTANCE, NEW_PRICE),
                    ORIGIN_BRANCH_ID);

            assertEquals(NEW_DISTANCE, route.getDistanceKm());
            assertEquals(NEW_PRICE, route.getTicketPrice());
            verify(outboxService).record(EventTypes.AGGREGATE_ROUTE, ROUTE_ID, EventTypes.ROUTE_UPDATED,
                    RouteEventPayload.fromEntity(route));
            assertEquals(NEW_PRICE, response.getTicketPrice());
        }

        @Test
        void destinationBranchAdmin_throwsForbidden() {
            RouteEntity route = route(false);
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
            UpdateRouteRequest request = new UpdateRouteRequest(NEW_DISTANCE, NEW_PRICE);

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.ROUTE_NOT_IN_BRANCH,
                    () -> routeService.update(ROUTE_ID, request, DESTINATION_BRANCH_ID));

            assertEquals(PRICE, route.getTicketPrice());
            verifyNoInteractions(outboxService);
        }

        @Test
        void deletedRoute_throwsNotFound() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(true)));
            UpdateRouteRequest request = new UpdateRouteRequest(NEW_DISTANCE, NEW_PRICE);

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND,
                    () -> routeService.update(ROUTE_ID, request, ORIGIN_BRANCH_ID));

            verifyNoInteractions(outboxService);
        }

        @Test
        void unknownRoute_throwsNotFound() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.empty());
            UpdateRouteRequest request = new UpdateRouteRequest(NEW_DISTANCE, NEW_PRICE);

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND,
                    () -> routeService.update(ROUTE_ID, request, ORIGIN_BRANCH_ID));
        }
    }

    @Nested
    class Delete {

        @Test
        void routeWithoutTrips_isSoftDeletedAndRecordsEvent() {
            RouteEntity route = route(false);
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
            when(tripClient.countTripsByRoute(ROUTE_ID)).thenReturn(0L);
            runCompensationsInline();

            routeService.delete(ROUTE_ID, ORIGIN_BRANCH_ID);

            assertTrue(route.isDeleted());
            assertNotNull(route.getDeletedAt());
            verify(outboxService).record(EventTypes.AGGREGATE_ROUTE, ROUTE_ID, EventTypes.ROUTE_DELETED,
                    RouteEventPayload.fromEntity(route));
            verify(routeRepository, never()).delete(any(RouteEntity.class));
        }

        @Test
        void routeWithTrips_throwsRouteHasTrips() {
            RouteEntity route = route(false);
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));
            when(tripClient.countTripsByRoute(ROUTE_ID)).thenReturn(3L);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.ROUTE_HAS_TRIPS,
                    () -> routeService.delete(ROUTE_ID, ORIGIN_BRANCH_ID));

            assertFalse(route.isDeleted());
            verifyNoInteractions(transactionTemplate, outboxService);
        }

        @Test
        void routeFromOtherBranch_throwsForbiddenWithoutAskingTrip() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(false)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.ROUTE_NOT_IN_BRANCH,
                    () -> routeService.delete(ROUTE_ID, DESTINATION_BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }

        @Test
        void alreadyDeletedRoute_throwsNotFound() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(true)));

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND,
                    () -> routeService.delete(ROUTE_ID, ORIGIN_BRANCH_ID));

            verifyNoInteractions(tripClient, transactionTemplate, outboxService);
        }
    }

    @Nested
    class Search {

        @Test
        void outOfRangePaging_isClampedAndSortedByNewest() {
            when(routeRepository.findAll(ArgumentMatchers.<Specification<RouteEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(route(false)), PageRequest.of(0, 1), 1));

            PageResponse<RouteResponse> result = routeService.search(ORIGIN_BRANCH_ID, null, -1, 0);

            verify(routeRepository).findAll(ArgumentMatchers.<Specification<RouteEntity>>any(),
                    pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(1, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
            assertEquals(1, result.getContent().size());
            assertEquals(ROUTE_ID, result.getContent().getFirst().getId());
        }

        @Test
        void sizeAboveLimit_isCappedAtOneHundred() {
            when(routeRepository.findAll(ArgumentMatchers.<Specification<RouteEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 100), 0));

            routeService.search(null, DESTINATION_BRANCH_ID, 1, 250);

            verify(routeRepository).findAll(ArgumentMatchers.<Specification<RouteEntity>>any(),
                    pageableCaptor.capture());
            assertEquals(1, pageableCaptor.getValue().getPageNumber());
            assertEquals(100, pageableCaptor.getValue().getPageSize());
        }
    }

    @Nested
    class FindById {

        @Test
        void existingRoute_returnsRoute() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(false)));

            RouteResponse response = routeService.findById(ROUTE_ID);

            assertEquals(ROUTE_ID, response.getId());
            assertEquals(DISTANCE, response.getDistanceKm());
        }

        @Test
        void deletedRoute_throwsNotFound() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(true)));

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND, () -> routeService.findById(ROUTE_ID));
        }
    }

    @Nested
    class FindInternalById {

        @Test
        void existingRoute_includesBranchNames() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(false)));
            when(branchProjectionRepository.findById(ORIGIN_BRANCH_ID))
                    .thenReturn(Optional.of(branch(ORIGIN_BRANCH_ID, ORIGIN_NAME, true)));
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID))
                    .thenReturn(Optional.of(branch(DESTINATION_BRANCH_ID, DESTINATION_NAME, true)));

            InternalRouteResponse response = routeService.findInternalById(ROUTE_ID);

            assertEquals(ROUTE_ID, response.getId());
            assertEquals(ORIGIN_NAME, response.getOriginBranchName());
            assertEquals(DESTINATION_NAME, response.getDestinationBranchName());
            assertEquals(DISTANCE, response.getDistanceKm());
            assertEquals(PRICE, response.getTicketPrice());
        }

        @Test
        void branchMissingInProjection_returnsNullName() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(false)));
            when(branchProjectionRepository.findById(ORIGIN_BRANCH_ID))
                    .thenReturn(Optional.of(branch(ORIGIN_BRANCH_ID, ORIGIN_NAME, true)));
            when(branchProjectionRepository.findById(DESTINATION_BRANCH_ID)).thenReturn(Optional.empty());

            InternalRouteResponse response = routeService.findInternalById(ROUTE_ID);

            assertEquals(ORIGIN_NAME, response.getOriginBranchName());
            assertNull(response.getDestinationBranchName());
        }

        @Test
        void deletedRoute_throwsNotFound() {
            when(routeRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route(true)));

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND,
                    () -> routeService.findInternalById(ROUTE_ID));

            verifyNoInteractions(branchProjectionRepository);
        }
    }

    private static RouteEntity route(boolean deleted) {
        return RouteEntity.builder()
                .id(ROUTE_ID)
                .originBranchId(ORIGIN_BRANCH_ID)
                .destinationBranchId(DESTINATION_BRANCH_ID)
                .distanceKm(DISTANCE)
                .ticketPrice(PRICE)
                .createdBy(USER_ID)
                .deleted(deleted)
                .build();
    }

    private static BranchProjectionEntity branch(UUID id, String name, boolean active) {
        return BranchProjectionEntity.builder()
                .id(id)
                .name(name)
                .active(active)
                .build();
    }

    private static CreateRouteRequest createRequest() {
        return new CreateRouteRequest(DESTINATION_BRANCH_ID, DISTANCE, PRICE);
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
