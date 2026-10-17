package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.BusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusRepository extends JpaRepository<BusEntity, UUID> {

    boolean existsByPlateIgnoreCase(String plate);
}
