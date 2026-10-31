package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseType;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
public class DriverResponse {
    UUID id;
    UUID userId;
    String fullName;
    String licenseNumber;
    LicenseType licenseType;
    LocalDate licenseExpiresOn;
    String phone;
    String email;
    BigDecimal baseSalaryPerTrip;
    String photoUrl;
    UUID branchId;
    boolean active;
    Instant createdAt;

    public static DriverResponse fromEntity(DriverEntity driver, String photoUrl) {
        return new DriverResponse(
                driver.getId(),
                driver.getUserId(),
                driver.getFullName(),
                driver.getLicenseNumber(),
                driver.getLicenseType(),
                driver.getLicenseExpiresOn(),
                driver.getPhone(),
                driver.getEmail(),
                driver.getBaseSalaryPerTrip(),
                photoUrl,
                driver.getBranchId(),
                driver.isActive(),
                driver.getCreatedAt());
    }
}
