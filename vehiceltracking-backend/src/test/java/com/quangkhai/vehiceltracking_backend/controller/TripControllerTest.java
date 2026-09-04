package com.quangkhai.vehiceltracking_backend.controller;

import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripCheckInRepository tripCheckInRepository;

    private Station station1;
    private Station station2;
    private Station station3;
    private Route route;
    private Vehicle vehicle;
    private Trip trip;

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
                .code("ROUTE-TEST-01")
                .name("Tuyến Bến Thành - Suối Tiên")
                .totalDistanceKm(18.5)
                .estimatedDurationMinutes(40.0)
                .build());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .plateNumber("51B-77788")
                .model("Thaco City Bus")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(35.0)
                .build());

        LocalDateTime now = LocalDateTime.now();

        trip = tripRepository.save(Trip.builder()
                .tripCode("TRIP-TEST-001")
                .route(route)
                .vehicle(vehicle)
                .startTime(now)
                .status(TripStatus.RUNNING)
                .build());

        // Chèn các điểm check-in không theo thứ tự (3, 1, 2) vào database
        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station3)
                .stopOrder(3)
                .scheduledArrivalTime(now.plusMinutes(40))
                .status(CheckInStatus.PENDING)
                .build());

        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station1)
                .stopOrder(1)
                .scheduledArrivalTime(now)
                .actualArrivalTime(now)
                .status(CheckInStatus.CHECKED_IN)
                .build());

        tripCheckInRepository.save(TripCheckIn.builder()
                .trip(trip)
                .station(station2)
                .stopOrder(2)
                .scheduledArrivalTime(now.plusMinutes(15))
                .status(CheckInStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("TC-002: GET /api/trips/{id} trả về danh sách checkIns đúng thứ tự stopOrder và đủ scheduledArrivalTime")
    void getTripById_ReturnsOrderedCheckInsWithScheduledArrivalTime() throws Exception {
        mockMvc.perform(get("/api/trips/{id}", trip.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trip.getId()))
                .andExpect(jsonPath("$.tripCode").value("TRIP-TEST-001"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.checkIns", hasSize(3)))
                // Kiểm tra stopOrder được sắp xếp tăng dần: 1 -> 2 -> 3
                .andExpect(jsonPath("$.checkIns[0].stopOrder").value(1))
                .andExpect(jsonPath("$.checkIns[0].stationId").value(station1.getId()))
                .andExpect(jsonPath("$.checkIns[0].status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.checkIns[0].scheduledArrivalTime").isNotEmpty())
                .andExpect(jsonPath("$.checkIns[0].actualArrivalTime").isNotEmpty())

                .andExpect(jsonPath("$.checkIns[1].stopOrder").value(2))
                .andExpect(jsonPath("$.checkIns[1].stationId").value(station2.getId()))
                .andExpect(jsonPath("$.checkIns[1].status").value("PENDING"))
                .andExpect(jsonPath("$.checkIns[1].scheduledArrivalTime").isNotEmpty())

                .andExpect(jsonPath("$.checkIns[2].stopOrder").value(3))
                .andExpect(jsonPath("$.checkIns[2].stationId").value(station3.getId()))
                .andExpect(jsonPath("$.checkIns[2].status").value("PENDING"))
                .andExpect(jsonPath("$.checkIns[2].scheduledArrivalTime").isNotEmpty());
    }
}
