package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.RouteEntity;
import com.urbantransport.fleet.dto.CreateRouteRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.RouteResponse;
import com.urbantransport.fleet.dto.UpdateRouteRequest;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.event.RouteEventPayload;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.RouteRepository;
import com.urbantransport.fleet.repository.RouteSpecifications;
import com.urbantransport.fleet.service.BranchProjectionService;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

        private static final int MAX_PAGE_SIZE = 100;

        private final RouteRepository routeRepository;
        private final BranchProjectionRepository branchProjectionRepository;
        private final BranchProjectionService branchProjectionService;
        private final OutboxService outboxService;

        @Override
        @Transactional
        public RouteResponse create(CreateRouteRequest request, UUID originBranchId, UUID createdBy) {
                UUID destinationBranchId = request.destinationBranchId();
                if (originBranchId.equals(destinationBranchId)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.SAME_ORIGIN_DESTINATION,
                                        "La sucursal destino no puede ser la misma que la de origen");
                }

                branchProjectionService.assertActive(originBranchId);
                assertValidDestination(destinationBranchId);

                if (routeRepository.existsByOriginBranchIdAndDestinationBranchIdAndDeletedFalse(originBranchId,
                                destinationBranchId)) {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.ROUTE_ALREADY_EXISTS,
                                        "Ya existe una ruta hacia esa sucursal destino");
                }

                RouteEntity route = routeRepository.save(RouteEntity.builder()
                                .originBranchId(originBranchId)
                                .destinationBranchId(destinationBranchId)
                                .distanceKm(request.distanceKm())
                                .ticketPrice(request.ticketPrice())
                                .createdBy(createdBy)
                                .build());

                outboxService.record(EventTypes.AGGREGATE_ROUTE, route.getId(), EventTypes.ROUTE_CREATED,
                                RouteEventPayload.fromEntity(route));

                return RouteResponse.fromEntity(route);
        }

        @Override
        @Transactional
        public RouteResponse update(UUID id, UpdateRouteRequest request, UUID branchId) {
                RouteEntity route = getOwnedOrThrow(id, branchId);

                route.setDistanceKm(request.distanceKm());
                route.setTicketPrice(request.ticketPrice());

                outboxService.record(EventTypes.AGGREGATE_ROUTE, route.getId(), EventTypes.ROUTE_UPDATED,
                                RouteEventPayload.fromEntity(route));

                return RouteResponse.fromEntity(route);
        }

        @Override
        @Transactional
        public void delete(UUID id, UUID branchId) {
                RouteEntity route = getOwnedOrThrow(id, branchId);
                // TODO: consultar a trip-service si la ruta tiene viajes asociados; 422
                // ROUTE_HAS_TRIPS si tiene, 503 si trip-service no responde

                route.setDeleted(true);
                route.setDeletedAt(Instant.now());

                outboxService.record(EventTypes.AGGREGATE_ROUTE, route.getId(), EventTypes.ROUTE_DELETED,
                                RouteEventPayload.fromEntity(route));
        }

        @Override
        @Transactional(readOnly = true)
        public PageResponse<RouteResponse> search(UUID originBranchId, UUID destinationBranchId, int page, int size) {
                PageRequest pageable = PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                                Sort.by(Sort.Direction.DESC, "createdAt"));
                return PageResponse.of(routeRepository
                                .findAll(RouteSpecifications.filter(originBranchId, destinationBranchId), pageable)
                                .map(RouteResponse::fromEntity));
        }

        @Override
        @Transactional(readOnly = true)
        public RouteResponse findById(UUID id) {
                return RouteResponse.fromEntity(getOrThrow(id));
        }

        private RouteEntity getOrThrow(UUID id) {
                return routeRepository.findById(id)
                                .filter(found -> !found.isDeleted())
                                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.ROUTE_NOT_FOUND,
                                                "Ruta no encontrada"));
        }

        private RouteEntity getOwnedOrThrow(UUID id, UUID branchId) {
                RouteEntity route = getOrThrow(id);
                if (!route.getOriginBranchId().equals(branchId)) {
                        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.ROUTE_NOT_IN_BRANCH,
                                        "Solo el administrador de la sucursal de origen puede gestionar la ruta");
                }
                return route;
        }

        private void assertValidDestination(UUID destinationBranchId) {
                BranchProjectionEntity destination = branchProjectionRepository.findById(destinationBranchId)
                                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                                                ErrorCodes.DESTINATION_BRANCH_INVALID,
                                                "La sucursal destino no existe"));
                if (!destination.isActive()) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.DESTINATION_BRANCH_INVALID,
                                        "La sucursal destino está inactiva");
                }
        }
}
