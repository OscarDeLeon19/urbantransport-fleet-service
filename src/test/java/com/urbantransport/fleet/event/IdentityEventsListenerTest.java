package com.urbantransport.fleet.event;

import com.urbantransport.fleet.service.BranchProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IdentityEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private BranchProjectionService branchProjectionService;

    private IdentityEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityEventsListener(jsonMapper, branchProjectionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.BRANCH_CREATED, EventTypes.BRANCH_UPDATED})
    void branchEvent_isAppliedToProjection(String eventType) {
        listener.onIdentityEvent(message(eventType,
                Map.of("branchId", BRANCH_ID.toString(), "name", "Central", "code", "CEN", "active", true)));

        verify(branchProjectionService).applyBranchEvent(EVENT_ID, eventType, BRANCH_ID, "Central", "CEN", true);
    }

    @Test
    void branchWithoutCode_isAppliedWithNullCode() {
        listener.onIdentityEvent(message(EventTypes.BRANCH_UPDATED,
                Map.of("branchId", BRANCH_ID.toString(), "name", "Central", "active", false)));

        verify(branchProjectionService).applyBranchEvent(EVENT_ID, EventTypes.BRANCH_UPDATED, BRANCH_ID, "Central",
                null, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UserCreated", "UserDeactivated", "BranchDeleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onIdentityEvent(message(eventType, Map.of("userId", UUID.randomUUID().toString())));

        verifyNoInteractions(branchProjectionService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onIdentityEvent("{no es json"));

        verifyNoInteractions(branchProjectionService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", BRANCH_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
