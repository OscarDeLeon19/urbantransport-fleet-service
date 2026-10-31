package com.urbantransport.fleet.event;

import com.urbantransport.fleet.service.BusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TripEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private BusService busService;

    private TripEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TripEventsListener(jsonMapper, busService);
    }

    @Test
    void tripStarted_marksBusInTransit() {
        listener.onTripEvent(message(EventTypes.TRIP_STARTED, Map.of("tripId", TRIP_ID.toString(),
                "busId", BUS_ID.toString())));

        verify(busService).applyTripStarted(EVENT_ID, EventTypes.TRIP_STARTED, BUS_ID);
    }

    @Test
    void tripCompleted_passesOdometerEnd() {
        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, Map.of("tripId", TRIP_ID.toString(),
                "busId", BUS_ID.toString(), "odometerEnd", 1250.75, "kmTraveled", 120.5)));

        verify(busService).applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, new BigDecimal("1250.75"));
    }

    @Test
    void tripCompletedWithIntegerOdometer_isConverted() {
        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, Map.of("busId", BUS_ID.toString(),
                "odometerEnd", 1300)));

        verify(busService).applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, new BigDecimal("1300"));
    }

    @Test
    void tripCompletedWithoutOdometer_passesNull() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("busId", BUS_ID.toString());
        payload.put("odometerEnd", null);

        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, payload));

        verify(busService).applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, BUS_ID, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TripScheduled", "TripUpdated", "TripDeleted", "PrivateTripRejected"})
    void otherTripEvent_isIgnored(String eventType) {
        listener.onTripEvent(message(eventType, Map.of("tripId", TRIP_ID.toString())));

        verifyNoInteractions(busService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onTripEvent("{\"eventId\":\"%s\",\"payload\":{}}".formatted(EVENT_ID));

        verifyNoInteractions(busService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onTripEvent("{no es json"));

        verifyNoInteractions(busService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", TRIP_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
