package com.urbantransport.fleet.service;

import java.util.UUID;

public interface BranchProjectionService {

    void applyBranchEvent(UUID eventId, String eventType, UUID branchId, String name, String code, boolean active);
}
