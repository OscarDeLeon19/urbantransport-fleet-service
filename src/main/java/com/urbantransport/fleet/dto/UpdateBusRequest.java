package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.BusStatus;
import jakarta.validation.constraints.*;

public record UpdateBusRequest(
        @NotBlank @Size(max = 15) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "solo letras, números y guion, sin espacios") String plate,
        @NotBlank @Size(max = 60) String brand,
        @NotBlank @Size(max = 60) String model,
        @NotNull Integer manufactureYear,
        @NotNull Integer capacity,
        BusStatus status,
        @Size(max = 500) String photoUrl) {
}
