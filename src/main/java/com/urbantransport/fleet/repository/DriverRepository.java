package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
}
