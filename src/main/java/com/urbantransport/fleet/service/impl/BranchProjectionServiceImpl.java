package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.domain.BranchProjectionEntity;
import com.urbantransport.fleet.domain.ProcessedEventEntity;
import com.urbantransport.fleet.repository.BranchProjectionRepository;
import com.urbantransport.fleet.repository.ProcessedEventRepository;
import com.urbantransport.fleet.service.BranchProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchProjectionServiceImpl implements BranchProjectionService {

    private final BranchProjectionRepository branchProjectionRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void applyBranchEvent(UUID eventId, String eventType, UUID branchId, String name, String code, boolean active) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }

        BranchProjectionEntity branch = branchProjectionRepository.findById(branchId)
                .orElseGet(() -> BranchProjectionEntity.builder().id(branchId).build());
        branch.setName(name);
        branch.setCode(code);
        branch.setActive(active);
        branchProjectionRepository.save(branch);

        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());

        log.info("Sucursal {} sincronizada desde {}", branchId, eventType);
    }
}
