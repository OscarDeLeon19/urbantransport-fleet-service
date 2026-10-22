package com.urbantransport.fleet.event;

import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
public class BusEventPayload {
    UUID busId;
    String plate;
    String brand;
    String model;
    int manufactureYear;
    int capacity;
    Short seatRows;
    Short seatColumns;
    Short aisleAfterColumn;
    BusStatus status;
    BigDecimal odometerKm;
    UUID branchId;

    public static BusEventPayload fromEntity(BusEntity bus) {
        return new BusEventPayload(bus.getId(), bus.getPlate(), bus.getBrand(), bus.getModel(),
                bus.getManufactureYear(), bus.getCapacity(), bus.getSeatRows(), bus.getSeatColumns(),
                bus.getAisleAfterColumn(), bus.getStatus(), bus.getOdometerKm(), bus.getBranchId());
    }
}
