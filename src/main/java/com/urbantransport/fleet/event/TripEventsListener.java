package com.urbantransport.fleet.event;

import com.urbantransport.fleet.service.BusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventsListener {

    private final JsonMapper jsonMapper;
    private final BusService busService;

    @KafkaListener(topics = "${app.kafka.topic.trip-events}")
    public void onTripEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();
        Map<String, Object> payload = event.payload();

        switch (eventType) {
            case EventTypes.TRIP_STARTED -> busService.applyTripStarted(event.eventId(), eventType, busIdOf(payload));
            case EventTypes.TRIP_COMPLETED -> busService.applyTripCompleted(event.eventId(), eventType,
                    busIdOf(payload), decimalOf(payload.get("odometerEnd")));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }

    private UUID busIdOf(Map<String, Object> payload) {
        return UUID.fromString(String.valueOf(payload.get("busId")));
    }

    private BigDecimal decimalOf(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
