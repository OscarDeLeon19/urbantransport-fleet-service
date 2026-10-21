package com.urbantransport.fleet.event;

import com.urbantransport.fleet.domain.RouteEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
public class RouteEventPayload {
    UUID routeId;
    UUID originBranchId;
    UUID destinationBranchId;
    BigDecimal distanceKm;
    BigDecimal ticketPrice;
    boolean deleted;

    public static RouteEventPayload fromEntity(RouteEntity route) {
        return new RouteEventPayload(route.getId(), route.getOriginBranchId(), route.getDestinationBranchId(),
                route.getDistanceKm(), route.getTicketPrice(), route.isDeleted());
    }
}
