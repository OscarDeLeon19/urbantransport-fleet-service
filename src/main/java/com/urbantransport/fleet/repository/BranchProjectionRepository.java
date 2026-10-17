package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.BranchProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BranchProjectionRepository extends JpaRepository<BranchProjectionEntity, UUID> {
}
