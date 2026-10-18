package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.BusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BusRepository extends JpaRepository<BusEntity, UUID>, JpaSpecificationExecutor<BusEntity> {

    boolean existsByPlateIgnoreCase(String plate);

    boolean existsByPlateIgnoreCaseAndIdNot(String plate, UUID id);
}
