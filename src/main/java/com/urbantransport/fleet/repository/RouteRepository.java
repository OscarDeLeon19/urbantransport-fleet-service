package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID>, JpaSpecificationExecutor<RouteEntity> {

    boolean existsByOriginBranchIdAndDestinationBranchIdAndDeletedFalse(UUID originBranchId, UUID destinationBranchId);
}
