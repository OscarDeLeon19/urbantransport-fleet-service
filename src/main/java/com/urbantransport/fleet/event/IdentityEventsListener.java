package com.urbantransport.fleet.event;

import com.urbantransport.fleet.service.BranchProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventsListener {

    private final JsonMapper jsonMapper;
    private final BranchProjectionService branchProjectionService;

    @KafkaListener(topics = "${app.kafka.topic.identity-events}")
    public void onIdentityEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        if (!EventTypes.BRANCH_CREATED.equals(event.eventType()) && !EventTypes.BRANCH_UPDATED.equals(event.eventType())) {
            log.debug("Evento {} ignorado", event.eventType());
            return;
        }
        Map<String, Object> payload = event.payload();
        branchProjectionService.applyBranchEvent(
                event.eventId(),
                event.eventType(),
                UUID.fromString(String.valueOf(payload.get("branchId"))),
                String.valueOf(payload.get("name")),
                payload.get("code") != null ? String.valueOf(payload.get("code")) : null,
                Boolean.parseBoolean(String.valueOf(payload.get("active"))));
    }
}
