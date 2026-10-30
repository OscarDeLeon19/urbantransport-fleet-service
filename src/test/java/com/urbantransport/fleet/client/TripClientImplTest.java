package com.urbantransport.fleet.client;

import com.urbantransport.fleet.config.TripClientProperties;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.exception.TripUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TripClientImplTest {

    private static final String BASE_URL = "http://trip.test";
    private static final String API_KEY = "clave-interna";
    private static final UUID BUS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROUTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BUS_URL = BASE_URL + "/internal/trips/active?busId=" + BUS_ID;

    private MockRestServiceServer server;
    private RestClient restClient;
    private CircuitBreaker circuitBreaker;
    private TripClientImpl tripClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        circuitBreaker = CircuitBreaker.of("trip", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .recordExceptions(TripUnavailableException.class)
                .build());
        tripClient = clientWithKey(API_KEY);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void countActiveTripsByBus_queriesActiveTripsByBus() {
        server.expect(requestTo(BUS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", API_KEY))
                .andRespond(withSuccess("{\"count\":2}", MediaType.APPLICATION_JSON));

        assertEquals(2, tripClient.countActiveTripsByBus(BUS_ID));
    }

    @Test
    void countActiveTripsByDriver_queriesActiveTripsByDriver() {
        server.expect(requestTo(BASE_URL + "/internal/trips/active?driverId=" + DRIVER_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", API_KEY))
                .andRespond(withSuccess("{\"count\":0}", MediaType.APPLICATION_JSON));

        assertEquals(0, tripClient.countActiveTripsByDriver(DRIVER_ID));
    }

    @Test
    void countTripsByRoute_queriesTripCountByRoute() {
        server.expect(requestTo(BASE_URL + "/internal/trips/count?routeId=" + ROUTE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", API_KEY))
                .andRespond(withSuccess("{\"count\":7,\"extra\":true}", MediaType.APPLICATION_JSON));

        assertEquals(7, tripClient.countTripsByRoute(ROUTE_ID));
    }

    @Test
    void missingApiKey_sendsEmptyHeader() {
        server.expect(requestTo(BUS_URL))
                .andExpect(header("X-Internal-Key", ""))
                .andRespond(withSuccess("{\"count\":1}", MediaType.APPLICATION_JSON));

        assertEquals(1, clientWithKey(null).countActiveTripsByBus(BUS_ID));
    }

    @Test
    void emptyBody_throwsBadGateway() {
        server.expect(requestTo(BUS_URL)).andRespond(withSuccess());

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR, () -> tripClient.countActiveTripsByBus(BUS_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void clientError_throwsBadGateway(int status) {
        server.expect(requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR, () -> tripClient.countActiveTripsByBus(BUS_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void serverError_throwsServiceUnavailable(int status) {
        server.expect(requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countActiveTripsByBus(BUS_ID));
    }

    @Test
    void connectionFailure_throwsServiceUnavailable() {
        server.expect(requestTo(BUS_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countActiveTripsByBus(BUS_ID));
    }

    @Test
    void openCircuit_failsFastWithoutCallingTrip() {
        circuitBreaker.transitionToOpenState();

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countTripsByRoute(ROUTE_ID));
    }

    @Test
    void repeatedServerErrors_openCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(BUS_URL)).andRespond(withServerError());

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countActiveTripsByBus(BUS_ID));
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countActiveTripsByBus(BUS_ID));

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.TRIP_UNAVAILABLE,
                () -> tripClient.countActiveTripsByBus(BUS_ID));
    }

    @Test
    void repeatedClientErrors_doNotOpenCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR, () -> tripClient.countActiveTripsByBus(BUS_ID));
        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.TRIP_ERROR, () -> tripClient.countActiveTripsByBus(BUS_ID));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    private TripClientImpl clientWithKey(String apiKey) {
        TripClientProperties properties = new TripClientProperties(BASE_URL, apiKey, Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new TripClientProperties.CircuitBreakerProperties(2, 2, 50f, Duration.ofSeconds(30)));
        return new TripClientImpl(restClient, circuitBreaker, properties);
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
