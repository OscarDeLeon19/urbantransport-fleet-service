package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.RouteEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
public class InternalRouteResponse {
    UUID id;
    UUID originBranchId;
    String originBranchName;
    UUID destinationBranchId;
    String destinationBranchName;
    BigDecimal distanceKm;
    BigDecimal ticketPrice;

    public static InternalRouteResponse fromEntity(RouteEntity route, String originBranchName,
                                                   String destinationBranchName) {
        return new InternalRouteResponse(
                route.getId(),
                route.getOriginBranchId(),
                originBranchName,
                route.getDestinationBranchId(),
                destinationBranchName,
                route.getDistanceKm(),
                route.getTicketPrice());
    }
}
