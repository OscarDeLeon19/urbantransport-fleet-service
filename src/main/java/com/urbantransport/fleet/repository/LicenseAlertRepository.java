package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.LicenseAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LicenseAlertRepository extends JpaRepository<LicenseAlertEntity, UUID> {

    @Modifying
    @Query("delete from LicenseAlertEntity a where a.driverId = :driverId")
    void deleteByDriverId(@Param("driverId") UUID driverId);
}
