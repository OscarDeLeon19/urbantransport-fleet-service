package com.urbantransport.fleet.web;

import com.urbantransport.fleet.AbstractIntegrationTest;
import com.urbantransport.fleet.domain.BusEntity;
import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusApiIT extends AbstractIntegrationTest {

    private UUID branchId;
    private UUID otherBranchId;

    @BeforeEach
    void seedBranches() {
        branchId = saveBranch("Central", true);
        otherBranchId = saveBranch("Xela", true);
    }

    @Test
    void create_persistsNormalizedBusAndRecordsEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/buses").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("p-123abc", 40, 10, 4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("P-123ABC"))
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.seatRows").value(10))
                .andExpect(jsonPath("$.status").value("DISPONIBLE"))
                .andExpect(jsonPath("$.branchId").value(branchId.toString()))
                .andReturn();

        UUID busId = idAt(result, "$.id");
        BusEntity saved = busRepository.findById(busId).orElseThrow();
        assertEquals("P-123ABC", saved.getPlate());
        assertEquals(branchId, saved.getBranchId());
        assertEquals(0, new BigDecimal("1500.50").compareTo(saved.getOdometerKm()));
        assertEquals(List.of("BusRegistered"), outboxEventTypes(busId));
    }

    @Test
    void create_withInvalidBody_returnsValidationError() throws Exception {
        mockMvc.perform(post("/buses").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"P 123\",\"capacity\":40}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("plate")))
                .andExpect(jsonPath("$.message", containsString("brand")))
                .andExpect(jsonPath("$.path").value("/buses"));

        assertEquals(0, busRepository.count());
    }

    @Test
    void create_withCapacityDifferentFromSeats_returnsInvalidCapacity() throws Exception {
        mockMvc.perform(post("/buses").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("P-123ABC", 41, 10, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_CAPACITY));

        assertEquals(0, busRepository.count());
    }

    @Test
    void create_withDuplicatedPlate_returnsConflict() throws Exception {
        saveBus(branchId, "P-123ABC", BusStatus.DISPONIBLE);

        mockMvc.perform(post("/buses").with(branchAdmin(otherBranchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("p-123abc", 40, 10, 4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PLATE_ALREADY_EXISTS));

        assertEquals(1, busRepository.count());
    }

    @Test
    void create_inBranchMissingFromProjection_returnsNotFound() throws Exception {
        mockMvc.perform(post("/buses").with(branchAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("P-123ABC", 40, 10, 4)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_NOT_FOUND));
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/buses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("P-123ABC", 40, 10, 4)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_asCashier_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/buses").with(userWithRole("CASHIER", branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("P-123ABC", 40, 10, 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        assertEquals(0, busRepository.count());
    }

    @Test
    void create_withTokenWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(post("/buses").with(userWithRole("BRANCH_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("P-123ABC", 40, 10, 4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));
    }

    @Test
    void search_asBranchAdmin_returnsOnlyOwnBranchWithFilters() throws Exception {
        saveBus(branchId, "P-001", BusStatus.DISPONIBLE);
        saveBus(branchId, "P-002", BusStatus.EN_TALLER);
        saveBus(otherBranchId, "P-003", BusStatus.DISPONIBLE);

        mockMvc.perform(get("/buses").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/buses").param("status", "EN_TALLER").with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].plate").value("P-002"));
    }

    @Test
    void search_asBranchAdminForOtherBranch_returnsBranchForbidden() throws Exception {
        mockMvc.perform(get("/buses").param("branchId", otherBranchId.toString()).with(branchAdmin(branchId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_FORBIDDEN));
    }

    @Test
    void search_asSystemAdmin_canFilterAnyBranch() throws Exception {
        saveBus(branchId, "P-001", BusStatus.DISPONIBLE);
        saveBus(otherBranchId, "P-003", BusStatus.DISPONIBLE);

        mockMvc.perform(get("/buses").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/buses").param("branchId", otherBranchId.toString()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].plate").value("P-003"));
    }

    @Test
    void findById_fromOtherBranch_isForbiddenExceptForSystemAdmin() throws Exception {
        UUID busId = saveBus(otherBranchId, "P-003", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(get("/buses/{id}", busId).with(branchAdmin(branchId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BUS_NOT_IN_BRANCH));

        mockMvc.perform(get("/buses/{id}", busId).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("P-003"));
    }

    @Test
    void update_withSameSeatLayout_updatesBusAndRecordsEvent() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(put("/buses/{id}", busId).with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("p-999xyz", 40, 10, 4, "EN_TALLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("P-999XYZ"))
                .andExpect(jsonPath("$.status").value("EN_TALLER"));

        BusEntity saved = busRepository.findById(busId).orElseThrow();
        assertEquals("P-999XYZ", saved.getPlate());
        assertEquals(BusStatus.EN_TALLER, saved.getStatus());
        assertEquals(List.of("BusUpdated"), outboxEventTypes(busId));
        verifyNoInteractions(tripClient);
    }

    @Test
    void update_seatLayoutOfBusWithActiveTrips_returnsLockedAndKeepsData() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();
        when(tripClient.countActiveTripsByBus(busId)).thenReturn(1L);

        mockMvc.perform(put("/buses/{id}", busId).with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("P-001", 48, 12, 4, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SEAT_LAYOUT_LOCKED));

        assertEquals(40, busRepository.findById(busId).orElseThrow().getCapacity());
        assertTrue(outboxEventTypes(busId).isEmpty());
    }

    @Test
    void deactivate_withoutActiveTrips_marksInactiveAndRecordsEvent() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(patch("/buses/{id}/deactivate", busId).with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVO"));

        assertEquals(BusStatus.INACTIVO, busRepository.findById(busId).orElseThrow().getStatus());
        assertEquals(List.of("BusDeactivated"), outboxEventTypes(busId));
    }

    @Test
    void deactivate_whenTripIsUnavailable_returnsServiceUnavailableAndKeepsBus() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();
        when(tripClient.countActiveTripsByBus(busId)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.TRIP_UNAVAILABLE, "El servicio de viajes no esta disponible"));

        mockMvc.perform(patch("/buses/{id}/deactivate", busId).with(branchAdmin(branchId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.TRIP_UNAVAILABLE));

        assertEquals(BusStatus.DISPONIBLE, busRepository.findById(busId).orElseThrow().getStatus());
        assertTrue(outboxEventTypes(busId).isEmpty());
    }

    @Test
    void uploadPhoto_storesKeyAndReturnsPresignedUrl() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.test/bus.png?firma").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        mockMvc.perform(multipart("/buses/{id}/photo", busId)
                        .file(new MockMultipartFile("file", "bus.png", "image/png", new byte[]{1, 2, 3}))
                        .with(branchAdmin(branchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://s3.test/bus.png?firma"));

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        String key = busRepository.findById(busId).orElseThrow().getPhotoUrl();
        assertTrue(key.startsWith("buses/" + busId + "/") && key.endsWith(".png"), key);
    }

    @Test
    void uploadPhoto_withUnsupportedType_returnsInvalidPhoto() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(multipart("/buses/{id}/photo", busId)
                        .file(new MockMultipartFile("file", "bus.gif", "image/gif", new byte[]{1, 2, 3}))
                        .with(branchAdmin(branchId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PHOTO));

        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadPhoto_withoutFile_returnsInvalidPhoto() throws Exception {
        UUID busId = saveBus(branchId, "P-001", BusStatus.DISPONIBLE).getId();

        mockMvc.perform(multipart("/buses/{id}/photo", busId).with(branchAdmin(branchId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PHOTO));
    }

    private static String createJson(String plate, int capacity, int rows, int columns) {
        return """
                {"plate":"%s","brand":"Volvo","model":"B8R","manufactureYear":2020,"capacity":%d,
                 "seatRows":%d,"seatColumns":%d,"aisleAfterColumn":2,"status":"DISPONIBLE","odometerKm":1500.50}
                """.formatted(plate, capacity, rows, columns);
    }

    private static String updateJson(String plate, int capacity, int rows, int columns, String status) {
        return """
                {"plate":"%s","brand":"Mercedes","model":"O500","manufactureYear":2021,"capacity":%d,
                 "seatRows":%d,"seatColumns":%d,"aisleAfterColumn":2,"status":%s}
                """.formatted(plate, capacity, rows, columns, status == null ? "null" : "\"" + status + "\"");
    }
}
