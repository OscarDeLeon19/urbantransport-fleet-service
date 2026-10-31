package com.urbantransport.fleet.dto;

import com.urbantransport.fleet.domain.LicenseType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDriverRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "solo letras, números y guion, sin espacios") String licenseNumber,
        @NotNull LicenseType licenseType,
        @NotNull LocalDate licenseExpiresOn,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "solo números, opcionalmente con + al inicio (8 a 15 dígitos)") String phone,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal baseSalaryPerTrip) {
}
