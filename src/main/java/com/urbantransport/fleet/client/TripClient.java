package com.urbantransport.fleet.client;

import java.util.UUID;

public interface TripClient {

    long countActiveTripsByBus(UUID busId);

    long countActiveTripsByDriver(UUID driverId);

    long countTripsByRoute(UUID routeId);
}
