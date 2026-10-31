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
    public static final String DRIVER_UPDATED = "DriverUpdated";
    public static final String DRIVER_DEACTIVATED = "DriverDeactivated";

    public static final String AGGREGATE_ROUTE = "Route";

    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_UPDATED = "RouteUpdated";
    public static final String ROUTE_DELETED = "RouteDeleted";

    public static final String BRANCH_CREATED = "BranchCreated";
    public static final String BRANCH_UPDATED = "BranchUpdated";

    public static final String TRIP_STARTED = "TripStarted";
    public static final String TRIP_COMPLETED = "TripCompleted";
}
