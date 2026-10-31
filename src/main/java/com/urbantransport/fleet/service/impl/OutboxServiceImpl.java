package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.config.FleetKafkaProperties;
import com.urbantransport.fleet.domain.OutboxEventEntity;
import com.urbantransport.fleet.event.EventEnvelope;
import com.urbantransport.fleet.repository.OutboxEventRepository;
import com.urbantransport.fleet.service.OutboxService;
import com.urbantransport.fleet.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private static final int EVENT_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final FleetKafkaProperties kafkaProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), aggregateId,
                EVENT_VERSION, correlationId(), payload);

        outboxEventRepository.save(OutboxEventEntity.builder()
                .id(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(kafkaProperties.fleetEvents())
                .payload(jsonMapper.writeValueAsString(envelope))
                .build());
    }

    private String correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        return current != null ? current : UUID.randomUUID().toString();
    }
}
