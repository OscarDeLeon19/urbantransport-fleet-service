package com.urbantransport.fleet.event;

import com.urbantransport.fleet.AbstractIntegrationTest;
import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KafkaEventsIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.kafka.topic.identity-events}")
    private String identityTopic;

    @Value("${app.kafka.topic.trip-events}")
    private String tripTopic;

    @Value("${app.kafka.topic.fleet-events}")
    private String fleetTopic;

    @Test
    void branchCreated_isProjectedAndDuplicatedEventIsIgnored() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();

        send(identityTopic, branchId, envelope(createdEventId, EventTypes.BRANCH_CREATED, branchId,
                Map.of("branchId", branchId.toString(), "name", "Central", "code", "CEN", "active", true)));
        send(identityTopic, branchId, envelope(createdEventId, EventTypes.BRANCH_CREATED, branchId,
                Map.of("branchId", branchId.toString(), "name", "Duplicado", "code", "DUP", "active", true)));
        send(identityTopic, branchId, envelope(UUID.randomUUID(), EventTypes.BRANCH_UPDATED, branchId,
                Map.of("branchId", branchId.toString(), "name", "Central", "code", "CEN", "active", false)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<BranchProjectionEntity> branch = branchProjectionRepository.findById(branchId);
            assertTrue(branch.isPresent());
            assertFalse(branch.get().isActive());
            assertEquals("Central", branch.get().getName());
            assertEquals("CEN", branch.get().getCode());
        });
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", createdEventId));
    }

    @Test
    void tripStartedAndCompleted_moveBusAndUpdateOdometer() throws Exception {
        UUID branchId = saveBranch("Central", true);
        BusEntity bus = saveBus(branchId, "P-001", BusStatus.DISPONIBLE);
        UUID tripId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_STARTED, tripId,
                Map.of("tripId", tripId.toString(), "busId", bus.getId().toString())));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(BusStatus.EN_TRANSITO, busRepository.findById(bus.getId()).orElseThrow().getStatus()));

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, tripId,
                Map.of("tripId", tripId.toString(), "busId", bus.getId().toString(), "odometerEnd", 1120.5,
                        "kmTraveled", 120.5)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            BusEntity updated = busRepository.findById(bus.getId()).orElseThrow();
            assertEquals(BusStatus.DISPONIBLE, updated.getStatus());
            assertEquals(0, new BigDecimal("1120.50").compareTo(updated.getOdometerKm()));
        });
        assertEquals(List.of("BusUpdated", "BusUpdated"), outboxEventTypes(bus.getId()));
    }

    @Test
    void outboxEvent_isPublishedToFleetEventsWithAggregateAsKey() throws Exception {
        UUID branchId = saveBranch("Central", true);
        MvcResult result = mockMvc.perform(post("/buses").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plate":"P-777KAF","brand":"Volvo","model":"B8R","manufactureYear":2020,"capacity":40,
                                 "seatRows":10,"seatColumns":4,"aisleAfterColumn":2,"status":"DISPONIBLE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID busId = idAt(result, "$.id");

        ConsumerRecord<String, String> record = awaitRecord(fleetTopic, busId.toString());

        JsonNode envelope = jsonMapper.readTree(record.value());
        assertEquals(EventTypes.BUS_REGISTERED, envelope.get("eventType").asString());
        assertEquals(busId.toString(), envelope.get("aggregateId").asString());
        assertEquals("P-777KAF", envelope.get("payload").get("plate").asString());
        assertEquals(10, envelope.get("payload").get("seatRows").asInt());
        await().atMost(TIMEOUT).untilAsserted(() -> assertNotNull(jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_events WHERE aggregate_id = ?", Instant.class, busId)));
    }

    @Test
    void malformedTripEvent_endsInDeadLetterTopic() throws Exception {
        UUID key = UUID.randomUUID();

        send(tripTopic, key, "{no es json");

        ConsumerRecord<String, String> record = awaitRecord(tripTopic + ".DLT", key.toString());
        assertEquals("{no es json", record.value());
    }

    private void send(String topic, UUID key, String value) throws Exception {
        kafkaTemplate.send(topic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private String envelope(UUID eventId, String eventType, UUID aggregateId, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "occurredAt", Instant.now().toString(),
                "aggregateId", aggregateId.toString(),
                "version", 1,
                "correlationId", UUID.randomUUID().toString(),
                "payload", payload));
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "it")) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).pollInSameThread().untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertTrue(received.stream().anyMatch(record -> key.equals(record.key())),
                        "no llegó el mensaje con key " + key + " a " + topic);
            });
        }
        return received.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
    }
}
