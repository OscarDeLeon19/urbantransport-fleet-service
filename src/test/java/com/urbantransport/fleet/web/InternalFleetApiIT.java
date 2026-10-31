package com.urbantransport.fleet.web;

import com.urbantransport.fleet.AbstractIntegrationTest;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.security.InternalApiKeyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalFleetApiIT extends AbstractIntegrationTest {

    @Value("${app.internal.api-key}")
    private String internalApiKey;

    private UUID branchId;
    private UUID destinationId;

    @BeforeEach
    void seedBranches() {
        branchId = saveBranch("Central", true);
        destinationId = saveBranch("Xela", true);
    }

    @Test
    void findBus_withInternalKey_returnsBusFromAnyBranch() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(get("/internal/buses/{id}", busId).header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(busId.toString()))
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.seatColumns").value(4));
    }

    @Test
    void findBus_withoutOrWithWrongKey_returnsUnauthorized() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(get("/internal/buses/{id}", busId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/buses/{id}", busId).header(InternalApiKeyFilter.HEADER, "otra-clave"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findBus_withUserToken_returnsForbidden() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(get("/internal/buses/{id}", busId).with(systemAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void findDriverByUser_returnsLinkedDriver() throws Exception {
        DriverEntity driver = saveDriver(branchId, "LIC-001", today().plusDays(90), true);

        mockMvc.perform(get("/internal/drivers/by-user/{userId}", driver.getUserId())
                        .header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(driver.getId().toString()))
                .andExpect(jsonPath("$.licenseNumber").value("LIC-001"));
    }

    @Test
    void findDriverByUser_unknownUser_returnsNotFound() throws Exception {
        mockMvc.perform(get("/internal/drivers/by-user/{userId}", UUID.randomUUID())
                        .header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DRIVER_NOT_FOUND));
    }

    @Test
    void findDriver_returnsDriverById() throws Exception {
        DriverEntity driver = saveDriver(branchId, "LIC-001", today().plusDays(90), false);

        mockMvc.perform(get("/internal/drivers/{id}", driver.getId())
                        .header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void findRoute_includesBranchNamesFromProjection() throws Exception {
        UUID routeId = saveRoute(branchId, destinationId, false).getId();

        mockMvc.perform(get("/internal/routes/{id}", routeId).header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originBranchName").value("Central"))
                .andExpect(jsonPath("$.destinationBranchName").value("Xela"))
                .andExpect(jsonPath("$.distanceKm").value(120.5))
                .andExpect(jsonPath("$.ticketPrice").value(45.0));
    }

    @Test
    void findRoute_deleted_returnsNotFound() throws Exception {
        UUID routeId = saveRoute(branchId, destinationId, true).getId();

        mockMvc.perform(get("/internal/routes/{id}", routeId).header(InternalApiKeyFilter.HEADER, internalApiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ROUTE_NOT_FOUND));
    }
}
