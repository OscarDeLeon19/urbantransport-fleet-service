package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.BusStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateBusRequest(
        @NotBlank @Size(max = 15) @Pattern(regexp = "^\\s*[A-Za-z0-9-]+\\s*$", message = "solo letras, números y guion") String plate,
        @NotBlank @Size(max = 60) String brand,
        @NotBlank @Size(max = 60) String model,
        @NotNull Integer manufactureYear,
        @NotNull Integer capacity,
        @NotNull BusStatus status,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal odometerKm,
        @Size(max = 500) String photoUrl) {
}
