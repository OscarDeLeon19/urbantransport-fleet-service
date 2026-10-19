package com.urbantransport.fleet.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventTypes {

    public static final String AGGREGATE_BUS = "Bus";

    public static final String BUS_REGISTERED = "BusRegistered";
    public static final String BUS_UPDATED = "BusUpdated";
    public static final String BUS_DEACTIVATED = "BusDeactivated";

    public static final String AGGREGATE_DRIVER = "Driver";

    public static final String DRIVER_REGISTERED = "DriverRegistered";

    public static final String BRANCH_CREATED = "BranchCreated";
    public static final String BRANCH_UPDATED = "BranchUpdated";

    // TODO: consumir eventos de trip-events: TripCompleted para sumar odometerKm y los de inicio/fin de viaje para pasar el bus a EN_TRANSITO y de vuelta a DISPONIBLE
}
