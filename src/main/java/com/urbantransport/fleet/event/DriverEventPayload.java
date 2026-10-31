package com.urbantransport.fleet.event;

import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseType;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Value
public class DriverEventPayload {
    UUID driverId;
    UUID userId;
    String fullName;
    String licenseNumber;
    LicenseType licenseType;
    LocalDate licenseExpiresOn;
    String phone;
    String email;
    BigDecimal baseSalaryPerTrip;
    UUID branchId;
    boolean active;

    public static DriverEventPayload fromEntity(DriverEntity driver) {
        return new DriverEventPayload(driver.getId(), driver.getUserId(), driver.getFullName(),
                driver.getLicenseNumber(), driver.getLicenseType(), driver.getLicenseExpiresOn(),
                driver.getPhone(), driver.getEmail(), driver.getBaseSalaryPerTrip(),
                driver.getBranchId(), driver.isActive());
    }
}
