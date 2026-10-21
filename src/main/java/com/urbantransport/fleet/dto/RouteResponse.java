package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.RouteEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class RouteResponse {
    UUID id;
    UUID originBranchId;
    UUID destinationBranchId;
    BigDecimal distanceKm;
    BigDecimal ticketPrice;
    UUID createdBy;
    Instant createdAt;

    public static RouteResponse fromEntity(RouteEntity route) {
        return new RouteResponse(
                route.getId(),
                route.getOriginBranchId(),
                route.getDestinationBranchId(),
                route.getDistanceKm(),
                route.getTicketPrice(),
                route.getCreatedBy(),
                route.getCreatedAt());
    }
}
