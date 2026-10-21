package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class BusResponse {
    UUID id;
    String plate;
    String brand;
    String model;
    int manufactureYear;
    int capacity;
    String photoUrl;
    BusStatus status;
    BigDecimal odometerKm;
    UUID branchId;
    Instant createdAt;

    public static BusResponse fromEntity(BusEntity bus) {
        return new BusResponse(
                bus.getId(),
                bus.getPlate(),
                bus.getBrand(),
                bus.getModel(),
                bus.getManufactureYear(),
                bus.getCapacity(),
                bus.getPhotoUrl(),
                bus.getStatus(),
                bus.getOdometerKm(),
                bus.getBranchId(),
                bus.getCreatedAt());
    }
}
