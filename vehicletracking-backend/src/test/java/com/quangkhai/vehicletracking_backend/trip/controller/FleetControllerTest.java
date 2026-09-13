package com.quangkhai.vehicletracking_backend.trip.controller;

import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.trip.service.TripService;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.vehicle.controller.VehicleController;
import com.quangkhai.vehicletracking_backend.vehicle.dto.VehicleResponse;
import com.quangkhai.vehicletracking_backend.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {VehicleController.class, TripController.class}, properties = {"app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
class FleetControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VehicleService vehicles;
    @MockitoBean TripService trips;
    @Test void createVehicle_returnsLocationAndDto() throws Exception {
        when(vehicles.create(any())).thenReturn(new VehicleResponse(4L, "51B12345", "Xe A", null, true, Instant.now(), Instant.now()));
        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plateNumber\":\"51b-123.45\",\"name\":\"Xe A\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/vehicles/4"))
                .andExpect(jsonPath("$.active").value(true));
    }
    @Test void invalidVehicle_doesNotReachService() throws Exception {
        mvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plateNumber\":\"<script>\",\"name\":\"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(vehicles);
    }
    @Test void tripRequiresPositiveIdsAndTimestamp() throws Exception {
        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":0,\"routeId\":-2}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":1,\"routeId\":2,\"scheduledDepartureAt\":\"invalid\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(trips);
    }
    @Test void tripCreation_parsesOffsetAndReturnsLocation() throws Exception {
        var schedule = Instant.parse("2026-09-14T01:00:00Z");
        var summary = new TripSummaryResponse(5L, 4L, "51B12345", 2L, "Tuyến A",
                TripStatus.SCHEDULED, schedule, schedule.plusSeconds(600), null, null, Instant.now());
        when(trips.create(any())).thenReturn(new TripDetailResponse(summary, List.of(), null));
        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":4,\"routeId\":2,\"scheduledDepartureAt\":\"2026-09-14T08:00:00+07:00\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/trips/5"))
                .andExpect(jsonPath("$.trip.status").value("SCHEDULED"));
        verify(trips).create(new TripCreateRequest(4L, 2L, schedule));
    }
    @Test void listTripsPassesVehicleFilter() throws Exception {
        when(trips.findAll(4L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/trips").param("vehicleId", "4")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(trips).findAll(4L);
    }
    @Test void conflictsUseProblemDetails() throws Exception {
        when(trips.start(5L)).thenThrow(new ResponseStatusException(CONFLICT, "Xe đang chạy chuyến khác."));
        mvc.perform(post("/api/v1/trips/5/start")).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Xe đang chạy chuyến khác."));
    }
    @Test void updateDeactivateAndLifecycleDelegate() throws Exception {
        mvc.perform(put("/api/v1/vehicles/4").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plateNumber\":\"51B12345\",\"name\":\"Xe sửa\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/vehicles/4")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/trips/5/complete")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/5/cancel")).andExpect(status().isOk());
        verify(vehicles).update(eq(4L), any()); verify(vehicles).deactivate(4);
        verify(trips).complete(5); verify(trips).cancel(5);
    }
}
