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
    Short seatRows;
    Short seatColumns;
    Short aisleAfterColumn;
    String photoUrl;
    BusStatus status;
    BigDecimal odometerKm;
    UUID branchId;
    Instant createdAt;

    public static BusResponse fromEntity(BusEntity bus, String photoUrl) {
        return new BusResponse(
                bus.getId(),
                bus.getPlate(),
                bus.getBrand(),
                bus.getModel(),
                bus.getManufactureYear(),
                bus.getCapacity(),
                bus.getSeatRows(),
                bus.getSeatColumns(),
                bus.getAisleAfterColumn(),
                photoUrl,
                bus.getStatus(),
                bus.getOdometerKm(),
                bus.getBranchId(),
                bus.getCreatedAt());
    }
}
