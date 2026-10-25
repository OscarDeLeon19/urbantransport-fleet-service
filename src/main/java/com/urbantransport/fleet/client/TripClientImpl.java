package com.urbantransport.fleet.client;

import com.urbantransport.fleet.config.TripClientProperties;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.exception.TripUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripClientImpl implements TripClient {

    private static final String ACTIVE_TRIPS_PATH = "/internal/trips/active";
    private static final String TRIPS_COUNT_PATH = "/internal/trips/count";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String UNAVAILABLE_MESSAGE =
            "El servicio de viajes no está disponible, intenta más tarde";

    private final RestClient tripRestClient;
    private final CircuitBreaker tripCircuitBreaker;
    private final TripClientProperties properties;

    @Override
    public long countActiveTripsByBus(UUID busId) {
        return execute(ACTIVE_TRIPS_PATH, "busId", busId);
    }

    @Override
    public long countActiveTripsByDriver(UUID driverId) {
        return execute(ACTIVE_TRIPS_PATH, "driverId", driverId);
    }

    @Override
    public long countTripsByRoute(UUID routeId) {
        return execute(TRIPS_COUNT_PATH, "routeId", routeId);
    }

    private long execute(String path, String param, UUID id) {
        try {
            return tripCircuitBreaker.executeSupplier(() -> fetchCount(path, param, id));
        } catch (CallNotPermittedException | TripUnavailableException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    private long fetchCount(String path, String param, UUID id) {
        try {
            String apiKey = properties.apiKey();
            TripCountResponse response = tripRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam(param, id).build())
                    .header(INTERNAL_KEY_HEADER, apiKey == null ? "" : apiKey)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new TripUnavailableException("Trip respondió " + res.getStatusCode());
                    })
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        log.error("Trip rechazó la consulta {}?{}={}: {} (revise INTERNAL_API_KEY)",
                                path, param, id, res.getStatusCode());
                        throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR,
                                "No se pudieron consultar los viajes, intenta más tarde");
                    })
                    .body(TripCountResponse.class);
            if (response == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR,
                        "No se pudieron consultar los viajes, intenta más tarde");
            }
            return response.count();
        } catch (ResourceAccessException exception) {
            throw new TripUnavailableException("No se pudo contactar a trip", exception);
        }
    }
}
