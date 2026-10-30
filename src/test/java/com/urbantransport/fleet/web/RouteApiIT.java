package com.urbantransport.fleet.web;

import com.urbantransport.fleet.AbstractIntegrationTest;
import com.urbantransport.fleet.domain.RouteEntity;
import com.urbantransport.fleet.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RouteApiIT extends AbstractIntegrationTest {

    private UUID originId;
    private UUID destinationId;
    private UUID inactiveId;

    @BeforeEach
    void seedBranches() {
        originId = saveBranch("Central", true);
        destinationId = saveBranch("Xela", true);
        inactiveId = saveBranch("Peten", false);
    }

    @Test
    void create_persistsRouteWithCreatorAndRecordsEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/routes").with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(destinationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originBranchId").value(originId.toString()))
                .andExpect(jsonPath("$.destinationBranchId").value(destinationId.toString()))
                .andExpect(jsonPath("$.createdBy").value(USER_ID.toString()))
                .andReturn();

        UUID routeId = idAt(result, "$.id");
        RouteEntity saved = routeRepository.findById(routeId).orElseThrow();
        assertEquals(originId, saved.getOriginBranchId());
        assertEquals(USER_ID, saved.getCreatedBy());
        assertEquals(List.of("RouteCreated"), outboxEventTypes(routeId));
    }

    @Test
    void create_toSameBranch_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/routes").with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(originId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SAME_ORIGIN_DESTINATION));
    }

    @Test
    void create_toInactiveBranch_returnsDestinationInvalid() throws Exception {
        mockMvc.perform(post("/routes").with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(inactiveId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DESTINATION_BRANCH_INVALID));

        assertEquals(0, routeRepository.count());
    }

    @Test
    void create_duplicatedRoute_returnsConflictUntilTheOldOneIsDeleted() throws Exception {
        RouteEntity existing = saveRoute(originId, destinationId, false);

        mockMvc.perform(post("/routes").with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(destinationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ROUTE_ALREADY_EXISTS));

        mockMvc.perform(delete("/routes/{id}", existing.getId()).with(branchAdmin(originId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/routes").with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(destinationId)))
                .andExpect(status().isCreated());
    }

    @Test
    void update_byDestinationAdmin_returnsForbidden() throws Exception {
        UUID routeId = saveRoute(originId, destinationId, false).getId();

        mockMvc.perform(put("/routes/{id}", routeId).with(branchAdmin(destinationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceKm\":130.00,\"ticketPrice\":50.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ROUTE_NOT_IN_BRANCH));
    }

    @Test
    void update_byOriginAdmin_changesPriceAndRecordsEvent() throws Exception {
        UUID routeId = saveRoute(originId, destinationId, false).getId();

        mockMvc.perform(put("/routes/{id}", routeId).with(branchAdmin(originId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceKm\":130.00,\"ticketPrice\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPrice").value(50.0));

        assertEquals(0, routeRepository.findById(routeId).orElseThrow().getTicketPrice()
                .compareTo(new BigDecimal("50.00")));
        assertEquals(List.of("RouteUpdated"), outboxEventTypes(routeId));
    }

    @Test
    void delete_withoutTrips_softDeletesAndHidesRoute() throws Exception {
        UUID routeId = saveRoute(originId, destinationId, false).getId();

        mockMvc.perform(delete("/routes/{id}", routeId).with(branchAdmin(originId)))
                .andExpect(status().isNoContent());

        RouteEntity deleted = routeRepository.findById(routeId).orElseThrow();
        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(List.of("RouteDeleted"), outboxEventTypes(routeId));

        mockMvc.perform(get("/routes/{id}", routeId).with(branchAdmin(originId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ROUTE_NOT_FOUND));
    }

    @Test
    void delete_withTrips_returnsUnprocessableAndKeepsRoute() throws Exception {
        UUID routeId = saveRoute(originId, destinationId, false).getId();
        when(tripClient.countTripsByRoute(routeId)).thenReturn(3L);

        mockMvc.perform(delete("/routes/{id}", routeId).with(branchAdmin(originId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ROUTE_HAS_TRIPS));

        assertFalse(routeRepository.findById(routeId).orElseThrow().isDeleted());
    }

    @Test
    void search_anyAuthenticatedUser_seesActiveRoutesWithFilters() throws Exception {
        UUID otherDestination = saveBranch("Coban", true);
        saveRoute(originId, destinationId, false);
        saveRoute(originId, otherDestination, false);
        saveRoute(destinationId, originId, false);
        saveRoute(originId, inactiveId, true);

        mockMvc.perform(get("/routes").with(userWithRole("CASHIER", destinationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/routes").param("originBranchId", originId.toString())
                        .with(userWithRole("CASHIER", destinationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/routes").param("originBranchId", originId.toString())
                        .param("destinationBranchId", otherDestination.toString())
                        .with(userWithRole("CASHIER", destinationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].destinationBranchId").value(otherDestination.toString()));
    }

    @Test
    void search_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/routes")).andExpect(status().isUnauthorized());
    }

    private static String createJson(UUID destinationBranchId) {
        return """
                {"destinationBranchId":"%s","distanceKm":120.50,"ticketPrice":45.00}
                """.formatted(destinationBranchId);
    }
}
