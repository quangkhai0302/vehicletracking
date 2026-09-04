package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
import com.quangkhai.vehiceltracking_backend.service.SimulatorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SimulatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStationRepository routeStationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripCheckInRepository tripCheckInRepository;

    @Autowired
    private SimulatorService simulatorService;

    private Station station1;
    private Station station2;
    private Station station3;
    private Route route;
    private Route singleStationRoute;
    private Vehicle vehicle;
    private Trip trip;
    private Trip singleStationTrip;

    @BeforeEach
    void setUp() {
        station1 = stationRepository.save(Station.builder()
                .code("ST-BEN-THANH")
                .name("Bến Thành")
                .latitude(10.7719)
                .longitude(106.6983)
                .radiusMeters(100.0)
                .stationType(StationType.START)
                .build());

        station2 = stationRepository.save(Station.builder()
                .code("ST-HANG-XANH")
                .name("Hàng Xanh")
                .latitude(10.8015)
                .longitude(106.7115)
                .radiusMeters(100.0)
                .stationType(StationType.STOP)
                .build());

        station3 = stationRepository.save(Station.builder()
                .code("ST-SUOI-TIEN")
                .name("Suối Tiên")
                .latitude(10.8659)
                .longitude(106.8028)
                .radiusMeters(100.0)
                .stationType(StationType.END)
                .build());

        route = routeRepository.save(Route.builder()
                .code("R01")
                .name("Bến Thành - Suối Tiên")
                .totalDistanceKm(15.0)
                .estimatedDurationMinutes(35.0)
                .build());

        routeStationRepository.save(RouteStation.builder()
                .route(route)
                .station(station1)
                .stopOrder(1)
                .distanceToNextKm(5.0)
                .estimatedTimeToNextMinutes(10.0)
                .build());

        routeStationRepository.save(RouteStation.builder()
                .route(route)
                .station(station2)
                .stopOrder(2)
                .distanceToNextKm(10.0)
                .estimatedTimeToNextMinutes(25.0)
                .build());

        routeStationRepository.save(RouteStation.builder()
                .route(route)
                .station(station3)
                .stopOrder(3)
                .distanceToNextKm(0.0)
                .estimatedTimeToNextMinutes(0.0)
                .build());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .plateNumber("51B-12345")
                .model("Thaco Bus")
                .status(VehicleStatus.IDLE)
                .currentLatitude(station1.getLatitude())
                .currentLongitude(station1.getLongitude())
                .currentSpeed(0.0)
                .currentHeading(0.0)
                .build());

        trip = tripRepository.save(Trip.builder()
                .tripCode("TRIP-TEST-001")
                .route(route)
                .vehicle(vehicle)
                .status(TripStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build());

        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station1)
                .stopOrder(1)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(LocalDateTime.now().plusMinutes(5))
                .build());

        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station2)
                .stopOrder(2)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(LocalDateTime.now().plusMinutes(15))
                .build());

        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station3)
                .stopOrder(3)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(LocalDateTime.now().plusMinutes(35))
                .build());

        // Route with only 1 station for testing route validation
        singleStationRoute = routeRepository.save(Route.builder()
                .code("R-SINGLE")
                .name("Tuyến 1 Trạm")
                .totalDistanceKm(0.0)
                .estimatedDurationMinutes(0.0)
                .build());

        routeStationRepository.save(RouteStation.builder()
                .route(singleStationRoute)
                .station(station1)
                .stopOrder(1)
                .build());

        singleStationTrip = tripRepository.save(Trip.builder()
                .tripCode("TRIP-SINGLE-001")
                .route(singleStationRoute)
                .vehicle(vehicle)
                .status(TripStatus.SCHEDULED)
                .build());
    }

    @AfterEach
    void tearDown() {
        simulatorService.stopScheduler();
    }

    @Test
    @DisplayName("TC-002: POST start simulation thành công trả về 200 với các trường typed")
    void startSimulation_Success_Returns200WithTypedFields() throws Exception {
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.simulationRunId").isNotEmpty())
                .andExpect(jsonPath("$.multiplier").value(1.0))
                .andExpect(jsonPath("$.currentWaypointIndex").value(0))
                .andExpect(jsonPath("$.lastPublishedSequence").value(0))
                .andExpect(jsonPath("$.message", containsString("bắt đầu")));
    }

    @Test
    @DisplayName("TC-002: POST start simulation khi không tìm thấy trip trả về 404 ProblemDetail")
    void startSimulation_TripNotFound_Returns404ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/simulator/start/{tripId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail", containsString("Không tìm thấy chuyến đi")));
    }

    @Test
    @DisplayName("TC-002: POST start simulation khi tuyến có < 2 trạm trả về 400 ProblemDetail")
    void startSimulation_RouteLessThan2Stations_Returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/simulator/start/{tripId}", singleStationTrip.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail", containsString("ít nhất 2 trạm dừng")));
    }

    @Test
    @DisplayName("TC-002: POST start simulation lần thứ hai trả về 409 ProblemDetail")
    void startSimulation_DuplicateStart_Returns409ProblemDetail() throws Exception {
        // Lần 1: Thành công
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        // Lần 2: Bị Conflict 409
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail", containsString("đã có phiên mô phỏng")));
    }

    @Test
    @DisplayName("TC-002: POST pause simulation khi chưa bắt đầu trả về 409 ProblemDetail")
    void pauseSimulation_WhenIdle_Returns409ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/simulator/pause/{tripId}", trip.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail", containsString("Không tìm thấy phiên mô phỏng đang hoạt động")));
    }

    @Test
    @DisplayName("TC-002: POST pause và resume simulation thành công trả về 200 với các trường typed")
    void pauseAndResumeSimulation_Success_Returns200WithTypedFields() throws Exception {
        // Start
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        // Pause
        mockMvc.perform(post("/api/simulator/pause/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.simulationRunId").isNotEmpty())
                .andExpect(jsonPath("$.message", containsString("tạm dừng")));

        // Resume
        mockMvc.perform(post("/api/simulator/resume/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.simulationRunId").isNotEmpty())
                .andExpect(jsonPath("$.message", containsString("tiếp tục")));
    }

    @Test
    @DisplayName("TC-006: POST multiplier với giá trị whitelist 1, 2, 5, 10 trả về 200")
    void setMultiplier_WhitelistedValues_Returns200WithTypedFields() throws Exception {
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        for (double m : new double[]{2.0, 5.0, 10.0, 1.0}) {
            mockMvc.perform(post("/api/simulator/multiplier/{tripId}", trip.getId())
                            .param("multiplier", String.valueOf(m)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.multiplier").value(m))
                    .andExpect(jsonPath("$.status").value("RUNNING"));
        }
    }

    @Test
    @DisplayName("TC-006: POST multiplier với giá trị không hợp lệ trả về 400 ProblemDetail")
    void setMultiplier_InvalidValues_Returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        // Giá trị ngoài whitelist và near-whitelist (REV-005)
        for (String invalidVal : new String[]{"0", "-1", "10.1", "3", "abc", "1.0000005", "9.9999995"}) {
            mockMvc.perform(post("/api/simulator/multiplier/{tripId}", trip.getId())
                            .param("multiplier", invalidVal))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"));
        }
    }

    @Test
    @DisplayName("TC-002: GET status trả về 200 với trạng thái IDLE hoặc RUNNING")
    void getStatus_Returns200WithPublicState() throws Exception {
        // Trước khi start: IDLE
        mockMvc.perform(get("/api/simulator/status/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.multiplier").value(1.0));

        // Start
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        // Sau khi start: RUNNING
        mockMvc.perform(get("/api/simulator/status/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.simulationRunId").isNotEmpty());
    }

    @Test
    @DisplayName("TC-002: POST reset simulation trả về 200 với IDLE state")
    void resetSimulation_Success_Returns200WithIdleState() throws Exception {
        // Start
        mockMvc.perform(post("/api/simulator/start/{tripId}", trip.getId()))
                .andExpect(status().isOk());

        // Reset
        mockMvc.perform(post("/api/simulator/reset/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.multiplier").value(1.0))
                .andExpect(jsonPath("$.message", containsString("đặt lại")));

        // Kiểm tra status endpoint cũng trả về IDLE
        mockMvc.perform(get("/api/simulator/status/{tripId}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
    }
}
