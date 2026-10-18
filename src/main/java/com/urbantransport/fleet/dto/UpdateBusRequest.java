package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.BusStatus;
import jakarta.validation.constraints.*;

public record UpdateBusRequest(
        @NotBlank @Size(max = 15) @Pattern(regexp = "^\s*[A-Za-z0-9-]+\s*$", message = "solo letras, números y guion") String plate,
        @NotBlank @Size(max = 60) String brand,
        @NotBlank @Size(max = 60) String model,
        @NotNull Integer manufactureYear,
        @NotNull Integer capacity,
        BusStatus status,
        @Size(max = 500) String photoUrl) {
}
