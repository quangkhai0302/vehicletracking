package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.dto.RouteRequestDto;
import com.quangkhai.vehiceltracking_backend.entity.Route;
import com.quangkhai.vehiceltracking_backend.entity.Station;
import com.quangkhai.vehiceltracking_backend.entity.Trip;
import com.quangkhai.vehiceltracking_backend.entity.Vehicle;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.RouteRepository;
import com.quangkhai.vehiceltracking_backend.repository.StationRepository;
import com.quangkhai.vehiceltracking_backend.repository.TripRepository;
import com.quangkhai.vehiceltracking_backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private Station startStation;
    private Station stopStation1;
    private Station stopStation2;
    private Station endStation;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        startStation = stationRepository.save(Station.builder()
                .code("ST-START-" + System.nanoTime())
                .name("Bến Xe Bắt Đầu")
                .latitude(10.80)
                .longitude(106.70)
                .radiusMeters(60.0)
                .stationType(StationType.START)
                .build());

        stopStation1 = stationRepository.save(Station.builder()
                .code("ST-STOP1-" + System.nanoTime())
                .name("Trạm Dừng 1")
                .latitude(10.82)
                .longitude(106.72)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        stopStation2 = stationRepository.save(Station.builder()
                .code("ST-STOP2-" + System.nanoTime())
                .name("Trạm Dừng 2")
                .latitude(10.83)
                .longitude(106.73)
                .radiusMeters(60.0)
                .stationType(StationType.STOP)
                .build());

        endStation = stationRepository.save(Station.builder()
                .code("ST-END-" + System.nanoTime())
                .name("Bến Xe Kết Thúc")
                .latitude(10.85)
                .longitude(106.75)
                .radiusMeters(70.0)
                .stationType(StationType.END)
                .build());

        testVehicle = vehicleRepository.save(Vehicle.builder()
                .plateNumber("51B-TEST-" + System.nanoTime())
                .model("Hyundai Universe")
                .status(VehicleStatus.IDLE)
                .build());
    }

    @Test
    @DisplayName("TC-001: POST /api/routes creates valid route with START -> STOP -> END and calculates metrics")
    void testCreateRoute_success() throws Exception {
        RouteRequestDto request = RouteRequestDto.builder()
                .code("r-int-01")
                .name("Tuyến Tích Hợp 01")
                .description("Mô tả tuyến")
                .stationIds(Arrays.asList(startStation.getId(), stopStation1.getId(), endStation.getId()))
                .build();

        MvcResult result = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("R-INT-01"))
                .andExpect(jsonPath("$.name").value("Tuyến Tích Hợp 01"))
                .andExpect(jsonPath("$.stations.length()").value(3))
                .andExpect(jsonPath("$.stations[0].stopOrder").value(1))
                .andExpect(jsonPath("$.stations[0].station.code").value(startStation.getCode()))
                .andExpect(jsonPath("$.stations[1].stopOrder").value(2))
                .andExpect(jsonPath("$.stations[1].station.code").value(stopStation1.getCode()))
                .andExpect(jsonPath("$.stations[2].stopOrder").value(3))
                .andExpect(jsonPath("$.stations[2].station.code").value(endStation.getCode()))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.get("totalDistanceKm").asDouble() > 0.0);
        assertTrue(json.get("estimatedDurationMinutes").asDouble() > 0.0);
    }

    @Test
    @DisplayName("TC-002: POST & PUT reject invalid DTO structure with 400 Problem Details")
    void testInvalidDtoStructure() throws Exception {
        // Missing name on POST
        RouteRequestDto noName = RouteRequestDto.builder()
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noName)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        // Less than 2 stations on POST
        RouteRequestDto oneStation = RouteRequestDto.builder()
                .name("Tuyến 1 trạm")
                .stationIds(List.of(startStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oneStation)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // Create base route for PUT testing
        RouteRequestDto baseRequest = RouteRequestDto.builder()
                .code("R-BASE-TC02")
                .name("Tuyến Ban Đầu TC02")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();
        MvcResult baseResult = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baseRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        long routeId = objectMapper.readTree(baseResult.getResponse().getContentAsString()).get("id").asLong();

        // Missing / blank name on PUT
        RouteRequestDto putBlankName = RouteRequestDto.builder()
                .name("   ")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putBlankName)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // Empty stationIds on PUT
        RouteRequestDto putEmptyStations = RouteRequestDto.builder()
                .name("Tuyến Tên Mới")
                .stationIds(List.of())
                .build();
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putEmptyStations)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // Less than 2 stations on PUT
        RouteRequestDto putOneStation = RouteRequestDto.builder()
                .name("Tuyến Tên Mới")
                .stationIds(List.of(startStation.getId()))
                .build();
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putOneStation)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));

        // Verify route before update remains unchanged
        mockMvc.perform(get("/api/routes/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tuyến Ban Đầu TC02"))
                .andExpect(jsonPath("$.code").value("R-BASE-TC02"))
                .andExpect(jsonPath("$.stations.length()").value(2));
    }

    @Test
    @DisplayName("TC-003: POST & PUT reject invalid station types with 400 Problem Details and preserve route on PUT")
    void testCreateAndPut_invalidStationTypes() throws Exception {
        // --- 1. POST tests ---
        // First is STOP
        RouteRequestDto firstNotStart = RouteRequestDto.builder()
                .name("Tuyến Sai Đầu")
                .stationIds(Arrays.asList(stopStation1.getId(), endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstNotStart)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // Last is STOP
        RouteRequestDto lastNotEnd = RouteRequestDto.builder()
                .name("Tuyến Sai Cuối")
                .stationIds(Arrays.asList(startStation.getId(), stopStation1.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lastNotEnd)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // Middle is START
        RouteRequestDto middleNotStop = RouteRequestDto.builder()
                .name("Tuyến Sai Giữa")
                .stationIds(Arrays.asList(startStation.getId(), startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(middleNotStop)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // --- 2. PUT tests ---
        // Create base route
        RouteRequestDto baseRequest = RouteRequestDto.builder()
                .code("R-BASE-TC03")
                .name("Tuyến Ban Đầu TC03")
                .stationIds(Arrays.asList(startStation.getId(), stopStation1.getId(), endStation.getId()))
                .build();
        MvcResult baseResult = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baseRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        long routeId = objectMapper.readTree(baseResult.getResponse().getContentAsString()).get("id").asLong();

        // PUT with first is STOP
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstNotStart)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // PUT with last is STOP
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lastNotEnd)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // PUT with middle is START
        mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(middleNotStop)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Tuyến phải bắt đầu bằng trạm START, kết thúc bằng trạm END và các trạm giữa phải là STOP."));

        // Verify route before update remains completely unchanged
        mockMvc.perform(get("/api/routes/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tuyến Ban Đầu TC03"))
                .andExpect(jsonPath("$.code").value("R-BASE-TC03"))
                .andExpect(jsonPath("$.stations.length()").value(3))
                .andExpect(jsonPath("$.stations[0].station.code").value(startStation.getCode()))
                .andExpect(jsonPath("$.stations[1].station.code").value(stopStation1.getCode()))
                .andExpect(jsonPath("$.stations[2].station.code").value(endStation.getCode()));
    }

    @Test
    @DisplayName("TC-004: Endpoints return 404 Problem Details when route or station does not exist")
    void testNotFoundHandling() throws Exception {
        // Non-existent station ID on create (POST)
        RouteRequestDto badStation = RouteRequestDto.builder()
                .name("Tuyến Có Trạm Ảo")
                .stationIds(Arrays.asList(startStation.getId(), 999999L, endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badStation)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy trạm với ID: 999999"));

        // Create base route for PUT testing
        RouteRequestDto baseRequest = RouteRequestDto.builder()
                .code("R-BASE-TC04")
                .name("Tuyến Ban Đầu TC04")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();
        MvcResult baseResult = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(baseRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        long existingRouteId = objectMapper.readTree(baseResult.getResponse().getContentAsString()).get("id").asLong();

        // Non-existent station ID on update (PUT)
        RouteRequestDto badStationPut = RouteRequestDto.builder()
                .name("Tuyến Sửa Trạm Ảo")
                .stationIds(Arrays.asList(startStation.getId(), 999999L, endStation.getId()))
                .build();

        mockMvc.perform(put("/api/routes/" + existingRouteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badStationPut)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy trạm với ID: 999999"));

        // Verify existing route remains unchanged
        mockMvc.perform(get("/api/routes/" + existingRouteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tuyến Ban Đầu TC04"))
                .andExpect(jsonPath("$.stations.length()").value(2));

        // Non-existent route ID on GET
        mockMvc.perform(get("/api/routes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));

        // Non-existent route ID on PUT
        RouteRequestDto validUpdate = RouteRequestDto.builder()
                .name("Tuyến Update")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(put("/api/routes/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));

        // Non-existent route ID on DELETE
        mockMvc.perform(delete("/api/routes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("TC-005: POST & PUT return 409 Problem Details on duplicate route code")
    void testDuplicateCodeHandling() throws Exception {
        // Create initial route
        RouteRequestDto initial = RouteRequestDto.builder()
                .code("R-UNIQUE-01")
                .name("Tuyến Unique 1")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initial)))
                .andExpect(status().isCreated());

        // Create second route with same code (different case / whitespace) via POST
        RouteRequestDto duplicateCreate = RouteRequestDto.builder()
                .code("  r-unique-01  ")
                .name("Tuyến Trùng Mã")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateCreate)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Mã tuyến đã tồn tại: R-UNIQUE-01"));

        // Create a distinct second route
        RouteRequestDto route2Req = RouteRequestDto.builder()
                .code("R-UNIQUE-02")
                .name("Tuyến Unique 2")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();
        MvcResult route2Result = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(route2Req)))
                .andExpect(status().isCreated())
                .andReturn();
        long route2Id = objectMapper.readTree(route2Result.getResponse().getContentAsString()).get("id").asLong();

        // Attempt PUT route 2 with duplicate code of route 1 (different case / whitespace)
        RouteRequestDto duplicatePut = RouteRequestDto.builder()
                .code("   r-unique-01   ")
                .name("Tuyến 2 Đổi Thành Trùng")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(put("/api/routes/" + route2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePut)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Mã tuyến đã tồn tại: R-UNIQUE-01"));

        // Verify route 2 code remains R-UNIQUE-02
        mockMvc.perform(get("/api/routes/" + route2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("R-UNIQUE-02"))
                .andExpect(jsonPath("$.name").value("Tuyến Unique 2"));

        // Verify PUT route 2 keeping its own code (normalized uppercase) succeeds
        RouteRequestDto keepCodePut = RouteRequestDto.builder()
                .code("  r-unique-02  ")
                .name("Tuyến 2 Đã Đổi Tên")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(put("/api/routes/" + route2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keepCodePut)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("R-UNIQUE-02"))
                .andExpect(jsonPath("$.name").value("Tuyến 2 Đã Đổi Tên"));
    }

    @Test
    @DisplayName("TC-006: PUT /api/routes/{id} updates topology and recalculates metrics while preserving id and createdAt")
    void testUpdateRoute_success() throws Exception {
        // Create route with [start, stop1, end]
        RouteRequestDto create = RouteRequestDto.builder()
                .code("R-ORIGINAL")
                .name("Tuyến Ban Đầu")
                .stationIds(Arrays.asList(startStation.getId(), stopStation1.getId(), endStation.getId()))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long routeId = createdJson.get("id").asLong();
        String createdAt = createdJson.get("createdAt").asText();

        // Update route with new name, description, and topology [start, stop2, stop1, end]
        RouteRequestDto update = RouteRequestDto.builder()
                .code("R-ORIGINAL-UPD")
                .name("Tuyến Đã Sửa Tên")
                .description("Mô tả cập nhật")
                .stationIds(Arrays.asList(startStation.getId(), stopStation2.getId(), stopStation1.getId(), endStation.getId()))
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/routes/" + routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeId))
                .andExpect(jsonPath("$.code").value("R-ORIGINAL-UPD"))
                .andExpect(jsonPath("$.name").value("Tuyến Đã Sửa Tên"))
                .andExpect(jsonPath("$.description").value("Mô tả cập nhật"))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.stations.length()").value(4))
                .andExpect(jsonPath("$.stations[0].station.code").value(startStation.getCode()))
                .andExpect(jsonPath("$.stations[1].station.code").value(stopStation2.getCode()))
                .andExpect(jsonPath("$.stations[2].station.code").value(stopStation1.getCode()))
                .andExpect(jsonPath("$.stations[3].station.code").value(endStation.getCode()))
                .andReturn();

        JsonNode updatedJson = objectMapper.readTree(updateResult.getResponse().getContentAsString());
        assertTrue(updatedJson.get("totalDistanceKm").asDouble() > 0.0);
    }

    @Test
    @DisplayName("TC-007: PUT /api/routes/{id} returns 409 Conflict when route is referenced by a Trip")
    void testUpdateRoute_referencedByTrip() throws Exception {
        // Create route
        Route route = routeRepository.save(Route.builder()
                .code("R-TRIP-LOCKED")
                .name("Tuyến Có Trip")
                .build());

        // Create trip referencing route
        tripRepository.save(Trip.builder()
                .tripCode("TRIP-LOCK-01-" + System.nanoTime())
                .route(route)
                .vehicle(testVehicle)
                .status(TripStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build());

        RouteRequestDto update = RouteRequestDto.builder()
                .name("Sửa Tuyến Bị Khóa")
                .stationIds(Arrays.asList(startStation.getId(), endStation.getId()))
                .build();

        mockMvc.perform(put("/api/routes/" + route.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi."));
    }

    @Test
    @DisplayName("TC-008: DELETE /api/routes/{id} safe delete: 409 if route has Trip, 204 if unreferenced")
    void testDeleteRoute_safeDelete() throws Exception {
        // Unreferenced route
        Route unreferenced = routeRepository.save(Route.builder()
                .code("R-TO-DELETE")
                .name("Tuyến Xóa")
                .build());

        mockMvc.perform(delete("/api/routes/" + unreferenced.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/routes/" + unreferenced.getId()))
                .andExpect(status().isNotFound());

        // Referenced route
        Route referenced = routeRepository.save(Route.builder()
                .code("R-REFERENCED")
                .name("Tuyến Không Thể Xóa")
                .build());

        tripRepository.save(Trip.builder()
                .tripCode("TRIP-LOCK-02-" + System.nanoTime())
                .route(referenced)
                .vehicle(testVehicle)
                .status(TripStatus.SCHEDULED)
                .startTime(LocalDateTime.now())
                .build());

        mockMvc.perform(delete("/api/routes/" + referenced.getId()))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Không thể sửa hoặc xóa tuyến đã được gán cho chuyến đi."));

        // Verify referenced route is still intact
        assertTrue(routeRepository.existsById(referenced.getId()));
    }

    @Test
    @DisplayName("TC-009: GET /api/routes and GET /api/routes/{id} return stations strictly ordered by stopOrder ASC")
    void testGetRoutes_orderedStations() throws Exception {
        RouteRequestDto create = RouteRequestDto.builder()
                .code("R-ORDER-TEST")
                .name("Tuyến Test Thứ Tự")
                .stationIds(Arrays.asList(startStation.getId(), stopStation1.getId(), stopStation2.getId(), endStation.getId()))
                .build();

        MvcResult result = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        long routeId = created.get("id").asLong();

        // 1. Verify GET /api/routes/{id} detail endpoint
        mockMvc.perform(get("/api/routes/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stations[0].stopOrder").value(1))
                .andExpect(jsonPath("$.stations[1].stopOrder").value(2))
                .andExpect(jsonPath("$.stations[2].stopOrder").value(3))
                .andExpect(jsonPath("$.stations[3].stopOrder").value(4));

        // 2. Verify GET /api/routes list endpoint
        MvcResult listResult = mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode foundRoute = null;
        for (JsonNode item : listJson) {
            if (item.has("id") && item.get("id").asLong() == routeId) {
                foundRoute = item;
                break;
            }
        }
        assertNotNull(foundRoute, "Tuyến R-ORDER-TEST phải có mặt trong kết quả GET /api/routes");
        JsonNode stationsInList = foundRoute.get("stations");
        assertNotNull(stationsInList);
        assertEquals(4, stationsInList.size());
        for (int i = 0; i < stationsInList.size(); i++) {
            assertEquals(i + 1, stationsInList.get(i).get("stopOrder").asInt(),
                    "Trạm tại vị trí " + i + " trong danh sách phải có stopOrder là " + (i + 1));
        }
    }
}
