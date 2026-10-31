package com.urbantransport.fleet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRouteRequest(
        @NotNull UUID destinationBranchId,
        @NotNull @Positive @Digits(integer = 6, fraction = 2) BigDecimal distanceKm,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal ticketPrice) {
}
