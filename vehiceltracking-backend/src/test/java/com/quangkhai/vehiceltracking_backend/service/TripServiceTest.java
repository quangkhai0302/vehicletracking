package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.TripDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripCheckInRepository tripCheckInRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteStationRepository routeStationRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private TripService tripService;

    private final ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Instant fixedInstant = Instant.parse("2026-09-04T03:00:00Z"); // 10:00:00 local
    private LocalDateTime fixedNow;

    private Route testRoute;
    private Vehicle testVehicle;
    private Station startStation;
    private Station stopStation;
    private Station endStation;
    private List<RouteStation> routeStations;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(zoneId);
        fixedNow = LocalDateTime.ofInstant(fixedInstant, zoneId);

        startStation = Station.builder()
                .id(1L)
                .code("ST-START")
                .name("Trạm Bến Thành")
                .latitude(10.7719)
                .longitude(106.6983)
                .radiusMeters(100.0)
                .stationType(StationType.START)
                .build();

        stopStation = Station.builder()
                .id(2L)
                .code("ST-STOP")
                .name("Trạm Hàng Xanh")
                .latitude(10.8015)
                .longitude(106.7115)
                .radiusMeters(100.0)
                .stationType(StationType.STOP)
                .build();

        endStation = Station.builder()
                .id(3L)
                .code("ST-END")
                .name("Trạm Suối Tiên")
                .latitude(10.8659)
                .longitude(106.8028)
                .radiusMeters(100.0)
                .stationType(StationType.END)
                .build();

        testRoute = Route.builder()
                .id(10L)
                .code("ROUTE-01")
                .name("Tuyến số 01")
                .totalDistanceKm(15.0)
                .estimatedDurationMinutes(30.0)
                .build();

        testVehicle = Vehicle.builder()
                .id(20L)
                .plateNumber("51B-12345")
                .model("Hyundai Universe")
                .status(VehicleStatus.IDLE)
                .currentSpeed(0.0)
                .build();

        RouteStation rs1 = RouteStation.builder()
                .id(101L)
                .route(testRoute)
                .station(startStation)
                .stopOrder(1)
                .distanceToNextKm(5.0)
                .estimatedTimeToNextMinutes(2.0)
                .build();

        RouteStation rs2 = RouteStation.builder()
                .id(102L)
                .route(testRoute)
                .station(stopStation)
                .stopOrder(2)
                .distanceToNextKm(10.0)
                .estimatedTimeToNextMinutes(3.0)
                .build();

        RouteStation rs3 = RouteStation.builder()
                .id(103L)
                .route(testRoute)
                .station(endStation)
                .stopOrder(3)
                .distanceToNextKm(0.0)
                .estimatedTimeToNextMinutes(0.0)
                .build();

        routeStations = Arrays.asList(rs1, rs2, rs3);
    }

    @Test
    @DisplayName("TC-001: Tạo Trip sinh schedule cumulative và startTime chính xác từ Clock")
    void createTrip_GeneratesCumulativeScheduledArrivalTimesAndStartTime() {
        when(routeRepository.findById(10L)).thenReturn(Optional.of(testRoute));
        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(testVehicle));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(10L)).thenReturn(routeStations);

        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        when(tripCheckInRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        TripDto result = tripService.createTrip(10L, 20L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(fixedNow, result.getStartTime());
        assertEquals(TripStatus.RUNNING, result.getStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, testVehicle.getStatus());

        // Kiểm tra check-ins được lưu
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TripCheckIn>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripCheckInRepository).saveAll(captor.capture());
        List<TripCheckIn> savedCheckIns = captor.getValue();

        assertEquals(3, savedCheckIns.size());

        // Stop 1: startTime (fixedNow)
        TripCheckIn ci1 = savedCheckIns.get(0);
        assertEquals(1, ci1.getStopOrder());
        assertEquals(startStation.getId(), ci1.getStation().getId());
        assertEquals(fixedNow, ci1.getScheduledArrivalTime());
        assertEquals(CheckInStatus.PENDING, ci1.getStatus());

        // Stop 2: startTime + 2 phút (120s)
        TripCheckIn ci2 = savedCheckIns.get(1);
        assertEquals(2, ci2.getStopOrder());
        assertEquals(stopStation.getId(), ci2.getStation().getId());
        assertEquals(fixedNow.plusSeconds(120), ci2.getScheduledArrivalTime());
        assertEquals(CheckInStatus.PENDING, ci2.getStatus());

        // Stop 3: startTime + 2 phút + 3 phút = startTime + 5 phút (300s)
        TripCheckIn ci3 = savedCheckIns.get(2);
        assertEquals(3, ci3.getStopOrder());
        assertEquals(endStation.getId(), ci3.getStation().getId());
        assertEquals(fixedNow.plusSeconds(300), ci3.getScheduledArrivalTime());
        assertEquals(CheckInStatus.PENDING, ci3.getStatus());
    }

    @Test
    @DisplayName("TC-006: completeTrip có tính idempotent, giữ nguyên endTime ban đầu khi gọi nhiều lần")
    void completeTrip_IsIdempotentAndPreservesInitialCompletionTime() {
        LocalDateTime completionTimeT1 = fixedNow.plusMinutes(25);
        LocalDateTime laterCallT2 = fixedNow.plusMinutes(40);

        Trip runningTrip = Trip.builder()
                .id(200L)
                .tripCode("TRIP-200")
                .route(testRoute)
                .vehicle(testVehicle)
                .startTime(fixedNow)
                .status(TripStatus.RUNNING)
                .checkIns(new ArrayList<>())
                .build();

        testVehicle.setStatus(VehicleStatus.IN_TRANSIT);
        testVehicle.setCurrentSpeed(45.0);

        when(tripRepository.findById(200L)).thenReturn(Optional.of(runningTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lần 1: hoàn thành tại T1
        tripService.completeTrip(200L, completionTimeT1);

        assertEquals(TripStatus.COMPLETED, runningTrip.getStatus());
        assertEquals(completionTimeT1, runningTrip.getEndTime());
        assertEquals(VehicleStatus.IDLE, testVehicle.getStatus());
        assertEquals(0.0, testVehicle.getCurrentSpeed());

        // Reset mock invocation count
        clearInvocations(tripRepository, vehicleRepository);

        // Lần 2: gọi lại với thời gian T2 > T1
        tripService.completeTrip(200L, laterCallT2);

        // Idempotent: endTime vẫn giữ T1, không bị ghi đè bởi T2
        assertEquals(TripStatus.COMPLETED, runningTrip.getStatus());
        assertEquals(completionTimeT1, runningTrip.getEndTime());
        // Không gọi lại save khi đã hoàn thành
        verify(tripRepository, never()).save(any(Trip.class));
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }
}
