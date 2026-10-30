package com.urbantransport.fleet.web;

import com.urbantransport.fleet.AbstractIntegrationTest;
import com.urbantransport.fleet.client.IdentityCreateUserRequest;
import com.urbantransport.fleet.client.IdentityUserResponse;
import com.urbantransport.fleet.domain.DriverEntity;
import com.urbantransport.fleet.domain.LicenseAlertEntity;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.repository.LicenseAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasLength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DriverApiIT extends AbstractIntegrationTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private LicenseAlertRepository licenseAlertRepository;

    private UUID branchId;
    private UUID otherBranchId;

    @BeforeEach
    void seedBranches() {
        branchId = saveBranch("Central", true);
        otherBranchId = saveBranch("Xela", true);
    }

    @Test
    void register_createsDriverAccountAlertsAndEvent() throws Exception {
        when(identityClient.createDriverUser(any())).thenReturn(
                new IdentityUserResponse(ACCOUNT_ID, "juan@mail.com", "Juan Perez", "DRIVER"));
        LocalDate expiresOn = today().plusDays(60);

        MvcResult result = mockMvc.perform(post("/drivers").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("lic-001", expiresOn)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driver.licenseNumber").value("LIC-001"))
                .andExpect(jsonPath("$.driver.email").value("juan@mail.com"))
                .andExpect(jsonPath("$.driver.userId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.driver.active").value(true))
                .andExpect(jsonPath("$.temporaryPassword", hasLength(12)))
                .andReturn();

        UUID driverId = idAt(result, "$.driver.id");
        DriverEntity saved = driverRepository.findById(driverId).orElseThrow();
        assertEquals(ACCOUNT_ID, saved.getUserId());
        assertEquals(branchId, saved.getBranchId());
        assertEquals(List.of(30, 15, 3), jdbcTemplate.queryForList(
                "SELECT days_before FROM license_alerts WHERE driver_id = ? ORDER BY days_before DESC",
                Integer.class, driverId));
        assertEquals(List.of("DriverRegistered"), outboxEventTypes(driverId));

        ArgumentCaptor<IdentityCreateUserRequest> request = ArgumentCaptor.forClass(IdentityCreateUserRequest.class);
        verify(identityClient).createDriverUser(request.capture());
        assertEquals("juan@mail.com", request.getValue().email());
    }

    @Test
    void register_whenIdentityRejectsEmail_compensatesAndLeavesNothing() throws Exception {
        when(identityClient.createDriverUser(any())).thenThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS, "El correo ya esta registrado"));

        mockMvc.perform(post("/drivers").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("LIC-001", today().plusDays(60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.USER_EMAIL_ALREADY_EXISTS));

        assertEquals(0, driverRepository.count());
        assertEquals(0, licenseAlertRepository.count());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_events"));
    }

    @Test
    void register_whenIdentityIsUnavailable_compensatesAndReturnsServiceUnavailable() throws Exception {
        when(identityClient.createDriverUser(any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.IDENTITY_UNAVAILABLE, "Identity no disponible"));

        mockMvc.perform(post("/drivers").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("LIC-001", today().plusDays(60))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.IDENTITY_UNAVAILABLE));

        assertEquals(0, driverRepository.count());
        assertEquals(0, licenseAlertRepository.count());
    }

    @Test
    void register_withDuplicatedLicense_returnsConflictWithoutCallingIdentity() throws Exception {
        saveDriver(otherBranchId, "LIC-001", today().plusDays(90), true);

        mockMvc.perform(post("/drivers").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("lic-001", today().plusDays(60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LICENSE_ALREADY_EXISTS));

        assertEquals(1, driverRepository.count());
        verify(identityClient, never()).createDriverUser(any());
    }

    @Test
    void register_withExpiredLicense_returnsLicenseExpired() throws Exception {
        mockMvc.perform(post("/drivers").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("LIC-001", today().minusDays(1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.LICENSE_EXPIRED));

        assertEquals(0, driverRepository.count());
    }

    @Test
    void update_withNewExpiration_replacesOnlyPendingAlerts() throws Exception {
        DriverEntity driver = saveDriver(branchId, "LIC-001", today().plusDays(40), true);
        licenseAlertRepository.save(alert(driver, 30, Instant.now()));
        licenseAlertRepository.save(alert(driver, 15, null));
        licenseAlertRepository.save(alert(driver, 3, null));
        LocalDate newExpiration = today().plusDays(20);

        mockMvc.perform(put("/drivers/{id}", driver.getId()).with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("lic-001", newExpiration)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseExpiresOn").value(newExpiration.toString()));

        assertEquals(1, count("SELECT COUNT(*) FROM license_alerts WHERE driver_id = ? AND sent_at IS NOT NULL",
                driver.getId()));
        assertEquals(List.of(15, 3), jdbcTemplate.queryForList(
                "SELECT days_before FROM license_alerts WHERE driver_id = ? AND sent_at IS NULL "
                        + "AND expires_on = ? ORDER BY days_before DESC",
                Integer.class, driver.getId(), newExpiration));
        assertEquals(2, count("SELECT COUNT(*) FROM license_alerts WHERE driver_id = ? AND sent_at IS NULL",
                driver.getId()));
        assertEquals(List.of("DriverUpdated"), outboxEventTypes(driver.getId()));
    }

    @Test
    void search_expiringInDays_returnsOwnBranchSortedBySoonestExpiration() throws Exception {
        saveDriver(branchId, "LIC-A", today().plusDays(10), true);
        saveDriver(branchId, "LIC-B", today().plusDays(90), true);
        saveDriver(branchId, "LIC-C", today().plusDays(5), false);
        saveDriver(otherBranchId, "LIC-D", today().plusDays(7), true);

        mockMvc.perform(get("/drivers").param("expiringInDays", "30").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].licenseNumber", contains("LIC-C", "LIC-A")));

        mockMvc.perform(get("/drivers").param("expiringInDays", "30").param("active", "true")
                        .with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].licenseNumber", contains("LIC-A")));

        mockMvc.perform(get("/drivers").param("expiringInDays", "30").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].licenseNumber", contains("LIC-C", "LIC-D", "LIC-A")));
    }

    @Test
    void search_withNegativeDays_returnsValidationError() throws Exception {
        mockMvc.perform(get("/drivers").param("expiringInDays", "-1").with(branchAdmin(branchId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void deactivate_withoutActiveTrips_marksInactiveAndRecordsEvent() throws Exception {
        UUID driverId = saveDriver(branchId, "LIC-001", today().plusDays(90), true).getId();

        mockMvc.perform(patch("/drivers/{id}/deactivate", driverId).with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertFalse(driverRepository.findById(driverId).orElseThrow().isActive());
        assertEquals(List.of("DriverDeactivated"), outboxEventTypes(driverId));
    }

    @Test
    void deactivate_withActiveTrips_returnsUnprocessableAndKeepsDriverActive() throws Exception {
        UUID driverId = saveDriver(branchId, "LIC-001", today().plusDays(90), true).getId();
        when(tripClient.countActiveTripsByDriver(driverId)).thenReturn(2L);

        mockMvc.perform(patch("/drivers/{id}/deactivate", driverId).with(branchAdmin(branchId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DRIVER_HAS_ACTIVE_TRIPS));

        assertTrue(driverRepository.findById(driverId).orElseThrow().isActive());
        assertTrue(outboxEventTypes(driverId).isEmpty());
    }

    @Test
    void findById_fromOtherBranch_returnsForbidden() throws Exception {
        UUID driverId = saveDriver(otherBranchId, "LIC-001", today().plusDays(90), true).getId();

        mockMvc.perform(get("/drivers/{id}", driverId).with(branchAdmin(branchId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DRIVER_NOT_IN_BRANCH));
    }

    private static LicenseAlertEntity alert(DriverEntity driver, int daysBefore, Instant sentAt) {
        return LicenseAlertEntity.builder()
                .driverId(driver.getId())
                .daysBefore((short) daysBefore)
                .expiresOn(driver.getLicenseExpiresOn())
                .notifyOn(driver.getLicenseExpiresOn().minusDays(daysBefore))
                .sentAt(sentAt)
                .build();
    }

    private static String createJson(String licenseNumber, LocalDate expiresOn) {
        return """
                {"fullName":"Juan Perez","licenseNumber":"%s","licenseType":"A","licenseExpiresOn":"%s",
                 "phone":"55551234","email":"Juan@Mail.com","baseSalaryPerTrip":150.00}
                """.formatted(licenseNumber, expiresOn);
    }

    private static String updateJson(String licenseNumber, LocalDate expiresOn) {
        return """
                {"licenseNumber":"%s","licenseType":"B","licenseExpiresOn":"%s","phone":"55559999",
                 "baseSalaryPerTrip":175.50}
                """.formatted(licenseNumber, expiresOn);
    }
}
