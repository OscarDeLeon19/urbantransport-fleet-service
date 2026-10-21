package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.LicenseType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateDriverRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "solo letras, números y guion, sin espacios") String licenseNumber,
        @NotNull LicenseType licenseType,
        @NotNull LocalDate licenseExpiresOn,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "solo números, opcionalmente con + al inicio (8 a 15 dígitos)") String phone,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal baseSalaryPerTrip) {
}
