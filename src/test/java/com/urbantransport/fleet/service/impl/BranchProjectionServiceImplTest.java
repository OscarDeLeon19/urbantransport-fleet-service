package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.ProcessedEventEntity;
import com.urbantransport.fleet.event.EventTypes;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchProjectionServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String NAME = "Central";
    private static final String CODE = "CEN";

    @Mock
    private BranchProjectionRepository branchProjectionRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<BranchProjectionEntity> branchCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private BranchProjectionServiceImpl branchProjectionService;

    @BeforeEach
    void setUp() {
        branchProjectionService = new BranchProjectionServiceImpl(branchProjectionRepository, processedEventRepository);
    }

    @Nested
    class ApplyBranchEvent {

        @Test
        void newBranch_isInsertedAndEventMarked() {
            when(branchProjectionRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            branchProjectionService.applyBranchEvent(EVENT_ID, EventTypes.BRANCH_CREATED, BRANCH_ID, NAME, CODE, true);

            verify(branchProjectionRepository).save(branchCaptor.capture());
            BranchProjectionEntity saved = branchCaptor.getValue();
            assertEquals(BRANCH_ID, saved.getId());
            assertEquals(NAME, saved.getName());
            assertEquals(CODE, saved.getCode());
            assertTrue(saved.isActive());
            assertMarkedProcessed(EventTypes.BRANCH_CREATED);
        }

        @Test
        void existingBranch_isOverwrittenWithEventData() {
            BranchProjectionEntity existing = branch(true);
            when(branchProjectionRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));

            branchProjectionService.applyBranchEvent(EVENT_ID, EventTypes.BRANCH_UPDATED, BRANCH_ID, "Central Norte",
                    "CNO", false);

            verify(branchProjectionRepository).save(branchCaptor.capture());
            assertSame(existing, branchCaptor.getValue());
            assertEquals("Central Norte", existing.getName());
            assertEquals("CNO", existing.getCode());
            assertFalse(existing.isActive());
            assertMarkedProcessed(EventTypes.BRANCH_UPDATED);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            branchProjectionService.applyBranchEvent(EVENT_ID, EventTypes.BRANCH_UPDATED, BRANCH_ID, NAME, CODE, true);

            verifyNoInteractions(branchProjectionRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class AssertActive {

        @Test
        void activeBranch_passes() {
            when(branchProjectionRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(true)));

            assertDoesNotThrow(() -> branchProjectionService.assertActive(BRANCH_ID));
        }

        @Test
        void inactiveBranch_throwsConflict() {
            when(branchProjectionRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(false)));

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.BRANCH_INACTIVE,
                    () -> branchProjectionService.assertActive(BRANCH_ID));
        }

        @Test
        void unknownBranch_throwsNotFound() {
            when(branchProjectionRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BRANCH_NOT_FOUND,
                    () -> branchProjectionService.assertActive(BRANCH_ID));
        }
    }

    private static BranchProjectionEntity branch(boolean active) {
        return BranchProjectionEntity.builder()
                .id(BRANCH_ID)
                .name(NAME)
                .code(CODE)
                .active(active)
                .build();
    }

    private void assertMarkedProcessed(String eventType) {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
        assertEquals(eventType, processedEventCaptor.getValue().getEventType());
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
