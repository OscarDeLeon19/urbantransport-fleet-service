package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.config.FleetKafkaProperties;
import com.urbantransport.fleet.domain.OutboxEventEntity;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.repository.OutboxEventRepository;
import com.urbantransport.fleet.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    private static final UUID AGGREGATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOPIC = "fleet-events";
    private static final String CORRELATION_ID = "corr-123";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxCaptor;

    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxServiceImpl(outboxEventRepository, jsonMapper,
                new FleetKafkaProperties(TOPIC, "identity-events", "trip-events"));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void record_savesEnvelopeWithEventDataAndTopic() {
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);

        outboxService.record(EventTypes.AGGREGATE_BUS, AGGREGATE_ID, EventTypes.BUS_REGISTERED,
                Map.of("plate", "P-123ABC"));

        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity saved = outboxCaptor.getValue();
        assertNotNull(saved.getId());
        assertEquals(EventTypes.AGGREGATE_BUS, saved.getAggregateType());
        assertEquals(AGGREGATE_ID, saved.getAggregateId());
        assertEquals(EventTypes.BUS_REGISTERED, saved.getEventType());
        assertEquals(TOPIC, saved.getTopic());
        assertNull(saved.getPublishedAt());
        assertEquals(0, saved.getAttempts());

        JsonNode envelope = jsonMapper.readTree(saved.getPayload());
        assertEquals(saved.getId().toString(), envelope.get("eventId").asString());
        assertEquals(EventTypes.BUS_REGISTERED, envelope.get("eventType").asString());
        assertEquals(AGGREGATE_ID.toString(), envelope.get("aggregateId").asString());
        assertEquals(1, envelope.get("version").asInt());
        assertEquals(CORRELATION_ID, envelope.get("correlationId").asString());
        assertFalse(envelope.get("occurredAt").isNull());
        assertEquals("P-123ABC", envelope.get("payload").get("plate").asString());
    }

    @Test
    void record_withoutCorrelationInContext_generatesOne() {
        outboxService.record(EventTypes.AGGREGATE_ROUTE, AGGREGATE_ID, EventTypes.ROUTE_DELETED, Map.of());

        verify(outboxEventRepository).save(outboxCaptor.capture());
        JsonNode envelope = jsonMapper.readTree(outboxCaptor.getValue().getPayload());
        String correlationId = envelope.get("correlationId").asString();
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }
}
