package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID>, JpaSpecificationExecutor<DriverEntity> {

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCaseAndIdNot(String licenseNumber, UUID id);
}
