package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.SimulatorResponseDto;
import com.quangkhai.vehiceltracking_backend.dto.StationEtaDto;
import com.quangkhai.vehiceltracking_backend.dto.VehicleTelemetryDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.StationType;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.exception.SimulatorConflictException;
import com.quangkhai.vehiceltracking_backend.exception.SimulatorNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulatorServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripCheckInRepository tripCheckInRepository;

    @Mock
    private RouteStationRepository routeStationRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private TrafficIncidentRepository incidentRepository;

    @Mock
    private GeofencingService geofencingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Clock clock;

    @InjectMocks
    private SimulatorService simulatorService;

    private final ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Instant fixedInstant = Instant.parse("2026-09-04T03:00:00Z"); // 10:00:00 local
    private LocalDateTime fixedNow;

    private Station stationA;
    private Station stationB;
    private Station stationC;
    private TripCheckIn checkInA;
    private TripCheckIn checkInB;
    private TripCheckIn checkInC;
    private Route route;
    private Vehicle vehicle;
    private Trip trip;
    private RouteStation routeStationA;
    private RouteStation routeStationB;
    private RouteStation routeStationC;
    private SimulatorService.SimulationSession session;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(zoneId);
        fixedNow = LocalDateTime.ofInstant(fixedInstant, zoneId);

        stationA = Station.builder()
                .id(1L)
                .code("STA")
                .name("Trạm A")
                .latitude(10.7719)
                .longitude(106.6983)
                .radiusMeters(100.0)
                .stationType(StationType.START)
                .build();

        stationB = Station.builder()
                .id(2L)
                .code("STB")
                .name("Trạm B")
                .latitude(10.8015)
                .longitude(106.7115)
                .radiusMeters(100.0)
                .stationType(StationType.STOP)
                .build();

        stationC = Station.builder()
                .id(3L)
                .code("STC")
                .name("Trạm C")
                .latitude(10.8659)
                .longitude(106.8028)
                .radiusMeters(100.0)
                .stationType(StationType.END)
                .build();

        checkInA = TripCheckIn.builder()
                .id(101L)
                .station(stationA)
                .stopOrder(1)
                .status(CheckInStatus.CHECKED_IN)
                .actualArrivalTime(fixedNow.minusMinutes(10))
                .scheduledArrivalTime(fixedNow.minusMinutes(10))
                .build();

        checkInB = TripCheckIn.builder()
                .id(102L)
                .station(stationB)
                .stopOrder(2)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow.plusMinutes(5))
                .build();

        checkInC = TripCheckIn.builder()
                .id(103L)
                .station(stationC)
                .stopOrder(3)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow.plusMinutes(15))
                .build();

        route = Route.builder()
                .id(1L)
                .code("R01")
                .name("Tuyến Mẫu")
                .build();

        vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IDLE)
                .currentLatitude(10.7719)
                .currentLongitude(106.6983)
                .currentSpeed(0.0)
                .currentHeading(0.0)
                .build();

        trip = Trip.builder()
                .id(500L)
                .tripCode("TRIP-500")
                .route(route)
                .vehicle(vehicle)
                .status(TripStatus.RUNNING)
                .build();

        routeStationA = RouteStation.builder().id(11L).route(route).station(stationA).stopOrder(1).build();
        routeStationB = RouteStation.builder().id(12L).route(route).station(stationB).stopOrder(2).build();
        routeStationC = RouteStation.builder().id(13L).route(route).station(stationC).stopOrder(3).build();

        session = SimulatorService.SimulationSession.builder()
                .tripId(500L)
                .vehicleId(50L)
                .plateNumber("51B-99999")
                .routeId(1L)
                .routeName("Tuyến Mẫu")
                .waypoints(new ArrayList<>())
                .currentWaypointIndex(0)
                .baseSpeedKmh(40.0)
                .speedMultiplier(1.0)
                .isPaused(false)
                .isCompleted(false)
                .alertedIncidentIds(new HashSet<>())
                .simulationRunId(UUID.randomUUID().toString())
                .lastPublishedSequence(0)
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        simulatorService.stopScheduler();
    }

    @Test
    @DisplayName("TC-003: calculateEtas tính ETA pending cumulative theo fixed Clock, không âm và tăng dần theo stopOrder")
    void calculateEtas_CalculatesOrderedCumulativePendingEta_NoNaNOrNegative() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));

        SimulatorService.Waypoint currentPos = SimulatorService.Waypoint.builder()
                .latitude(10.7850)
                .longitude(106.7050)
                .build();

        double effectiveSpeedKmh = 40.0; // ~11.11 m/s

        List<StationEtaDto> etas = simulatorService.calculateEtas(session, currentPos, effectiveSpeedKmh);

        assertNotNull(etas);
        assertEquals(3, etas.size());

        // Stop 1: CHECKED_IN
        StationEtaDto etaA = etas.get(0);
        assertEquals(1, etaA.getStopOrder());
        assertEquals(CheckInStatus.CHECKED_IN, etaA.getStatus());
        assertEquals(0.0, etaA.getDistanceRemainingMeters());
        assertEquals(0L, etaA.getEtaSeconds());
        assertEquals(checkInA.getActualArrivalTime(), etaA.getEstimatedArrivalTime());

        // Stop 2: PENDING
        StationEtaDto etaB = etas.get(1);
        assertEquals(2, etaB.getStopOrder());
        assertEquals(CheckInStatus.PENDING, etaB.getStatus());
        assertTrue(etaB.getDistanceRemainingMeters() > 0.0);
        assertTrue(etaB.getEtaSeconds() > 0L);
        assertNotNull(etaB.getEstimatedArrivalTime());
        assertEquals(fixedNow.plusSeconds(etaB.getEtaSeconds()), etaB.getEstimatedArrivalTime());

        // Stop 3: PENDING, khoảng cách và ETA phải lớn hơn Stop 2 vì là cumulative
        StationEtaDto etaC = etas.get(2);
        assertEquals(3, etaC.getStopOrder());
        assertEquals(CheckInStatus.PENDING, etaC.getStatus());
        assertTrue(etaC.getDistanceRemainingMeters() > etaB.getDistanceRemainingMeters());
        assertTrue(etaC.getEtaSeconds() > etaB.getEtaSeconds());
        assertEquals(fixedNow.plusSeconds(etaC.getEtaSeconds()), etaC.getEstimatedArrivalTime());

        // Đảm bảo không có giá trị NaN hoặc âm
        assertFalse(Double.isNaN(etaB.getDistanceRemainingMeters()));
        assertFalse(Double.isNaN(etaC.getDistanceRemainingMeters()));
        assertTrue(etaB.getEtaSeconds() >= 0);
        assertTrue(etaC.getEtaSeconds() >= 0);
    }

    @Test
    @DisplayName("TC-004: Tốc độ giảm do sự cố làm tăng ETA của các trạm PENDING và completion ETA")
    void calculateEtas_IncidentSpeedReduction_IncreasesEta() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));

        SimulatorService.Waypoint currentPos = SimulatorService.Waypoint.builder()
                .latitude(10.7850)
                .longitude(106.7050)
                .build();

        double normalSpeedKmh = 40.0;
        double incidentReducedSpeedKmh = 16.0; // Giảm 60% tốc độ do kẹt xe

        List<StationEtaDto> normalEtas = simulatorService.calculateEtas(session, currentPos, normalSpeedKmh);
        List<StationEtaDto> incidentEtas = simulatorService.calculateEtas(session, currentPos, incidentReducedSpeedKmh);

        // Trạm CHECKED_IN không bị ảnh hưởng bởi tốc độ
        assertEquals(normalEtas.get(0).getEtaSeconds(), incidentEtas.get(0).getEtaSeconds());
        assertEquals(normalEtas.get(0).getEstimatedArrivalTime(), incidentEtas.get(0).getEstimatedArrivalTime());

        // Trạm PENDING (Stop B và Stop C) có ETA tăng lên khi tốc độ giảm
        assertTrue(incidentEtas.get(1).getEtaSeconds() > normalEtas.get(1).getEtaSeconds(),
                "ETA của Stop B trong vùng sự cố phải lớn hơn khi bình thường");
        assertTrue(incidentEtas.get(2).getEtaSeconds() > normalEtas.get(2).getEtaSeconds(),
                "ETA của Stop C (đích đến) trong vùng sự cố phải lớn hơn khi bình thường");

        // Thời gian ước tính đến cũng muộn hơn
        assertTrue(incidentEtas.get(2).getEstimatedArrivalTime().isAfter(normalEtas.get(2).getEstimatedArrivalTime()),
                "Thời gian dự kiến về đích khi có sự cố phải muộn hơn bình thường");
    }

    @Test
    @DisplayName("TC-005: Tick cuối cùng chốt state và phát terminal telemetry chính xác một lần")
    void tickSingleSimulation_TerminalTick_EmitsTerminalTelemetryOnce() {
        // Cả 3 trạm đều đã check in
        checkInA.setStatus(CheckInStatus.CHECKED_IN);
        checkInB.setStatus(CheckInStatus.CHECKED_IN);
        checkInB.setActualArrivalTime(fixedNow.minusMinutes(3));
        checkInC.setStatus(CheckInStatus.CHECKED_IN);
        checkInC.setActualArrivalTime(fixedNow); // Vừa check-in trạm cuối

        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        // 2 waypoints: index 0 -> index 1 (terminal)
        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder()
                .latitude(10.8650)
                .longitude(106.8020)
                .build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder()
                .latitude(10.8659)
                .longitude(106.8028)
                .stationId(3L)
                .stationName("Trạm C")
                .stopOrder(3)
                .build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(5.0); // đủ để bước tới wp1

        // Thực hiện tick
        simulatorService.tickSingleSimulation(session);

        // Session đã được đánh dấu completed
        assertTrue(session.isCompleted());
        assertEquals(1, session.getCurrentWaypointIndex());

        // Telemetry được phát đi
        ArgumentCaptor<VehicleTelemetryDto> telemetryCaptor = ArgumentCaptor.forClass(VehicleTelemetryDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/telemetry"), telemetryCaptor.capture());
        VehicleTelemetryDto terminalTelemetry = telemetryCaptor.getValue();

        assertNotNull(terminalTelemetry);
        assertEquals(VehicleStatus.IDLE, terminalTelemetry.getStatus());
        assertEquals(TripStatus.COMPLETED, terminalTelemetry.getTripStatus());
        assertNull(terminalTelemetry.getTargetStationId());
        assertEquals("Đã hoàn thành", terminalTelemetry.getTargetStationName());
        assertEquals(0.0, terminalTelemetry.getDistanceToTargetMeters());
        assertEquals(0L, terminalTelemetry.getEtaSecondsToTarget());
        assertEquals(0L, terminalTelemetry.getEtaSecondsToCompletion());
        assertEquals(fixedNow, terminalTelemetry.getEstimatedCompletionTime());
        assertEquals(0.0, terminalTelemetry.getSpeed());

        // Tất cả các trạm trong telemetry đều CHECKED_IN
        assertEquals(3, terminalTelemetry.getStationsEta().size());
        assertTrue(terminalTelemetry.getStationsEta().stream()
                .allMatch(eta -> eta.getStatus() == CheckInStatus.CHECKED_IN));

        // Gọi lại tickSingleSimulation khi đã completed -> early return, không phát thêm telemetry
        reset(messagingTemplate);
        simulatorService.tickSingleSimulation(session);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("REV-001 / TC-005: Xe chạm waypoint cuối nhưng check-in vẫn PENDING -> không phát terminal telemetry và không kết thúc session")
    void tickSingleSimulation_ReachingEndWaypointWithPendingStops_DoesNotEmitTerminalTelemetry() {
        // Stop A đã checked in, nhưng Stop B và Stop C vẫn PENDING
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder()
                .latitude(10.7850)
                .longitude(106.7050)
                .build();
        SimulatorService.Waypoint wpFinal = SimulatorService.Waypoint.builder()
                .latitude(10.8659)
                .longitude(106.8028)
                .stationId(3L)
                .stationName("Trạm C")
                .stopOrder(3)
                .build();

        session.setWaypoints(Arrays.asList(wp0, wpFinal));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(10.0); // Nhảy thẳng tới wpFinal (index 1)

        simulatorService.tickSingleSimulation(session);

        // Đã bước tới cuối nhưng session KHÔNG được đánh dấu completed vì còn trạm PENDING
        assertFalse(session.isCompleted(), "Session không được completed khi còn trạm PENDING");
        assertEquals(1, session.getCurrentWaypointIndex());

        // Telemetry phát ra phải là RUNNING, không phải COMPLETED
        ArgumentCaptor<VehicleTelemetryDto> telemetryCaptor = ArgumentCaptor.forClass(VehicleTelemetryDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/telemetry"), telemetryCaptor.capture());
        VehicleTelemetryDto nonTerminalTelemetry = telemetryCaptor.getValue();

        assertNotNull(nonTerminalTelemetry);
        assertEquals(TripStatus.RUNNING, nonTerminalTelemetry.getTripStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, nonTerminalTelemetry.getStatus());
        assertEquals(2L, nonTerminalTelemetry.getTargetStationId());
        assertEquals("Trạm B", nonTerminalTelemetry.getTargetStationName());
        assertTrue(nonTerminalTelemetry.getEtaSecondsToCompletion() > 0L);
        assertNotEquals(0L, nonTerminalTelemetry.getEtaSecondsToCompletion());
    }

    @Test
    @DisplayName("REV-003 / TC-003 / TC-005: Non-terminal telemetry suy dẫn completion ETA từ trạm cuối cùng trong lịch trình")
    void tickSingleSimulation_NonTerminalTick_DerivesCompletionEtaFromFinalStop() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder()
                .latitude(10.7850)
                .longitude(106.7050)
                .build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder()
                .latitude(10.7860)
                .longitude(106.7060)
                .build();
        SimulatorService.Waypoint wp2 = SimulatorService.Waypoint.builder()
                .latitude(10.8659)
                .longitude(106.8028)
                .build();

        session.setWaypoints(Arrays.asList(wp0, wp1, wp2));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(1.0);

        simulatorService.tickSingleSimulation(session);

        assertFalse(session.isCompleted());
        ArgumentCaptor<VehicleTelemetryDto> telemetryCaptor = ArgumentCaptor.forClass(VehicleTelemetryDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/telemetry"), telemetryCaptor.capture());
        VehicleTelemetryDto telemetry = telemetryCaptor.getValue();

        assertNotNull(telemetry);
        assertEquals(TripStatus.RUNNING, telemetry.getTripStatus());
        assertEquals(VehicleStatus.IN_TRANSIT, telemetry.getStatus());
        assertEquals(2L, telemetry.getTargetStationId());
        assertEquals("Trạm B", telemetry.getTargetStationName());

        // Trạm cuối trong stationsEta là Trạm C
        List<StationEtaDto> stationEtas = telemetry.getStationsEta();
        assertEquals(3, stationEtas.size());
        StationEtaDto finalStopEta = stationEtas.get(2);
        assertEquals(3L, finalStopEta.getStationId());

        // Completion ETA phải khớp chính xác với ETA của trạm cuối
        assertEquals(finalStopEta.getEtaSeconds(), telemetry.getEtaSecondsToCompletion(),
                "Completion ETA phải bằng ETA của trạm cuối");
        assertEquals(finalStopEta.getEstimatedArrivalTime(), telemetry.getEstimatedCompletionTime(),
                "Thời gian dự kiến về đích phải bằng thời gian dự kiến đến trạm cuối");
        assertTrue(telemetry.getEtaSecondsToCompletion() > telemetry.getEtaSecondsToTarget(),
                "Thời gian về đích phải lớn hơn thời gian tới trạm kế tiếp");
    }

    @Test
    @DisplayName("TC-005: Simulator tick ở waypoint index 0 kiểm tra START station trước khi di chuyển")
    void tickSingleSimulation_AtWaypointIndexZero_ChecksStartStationBeforeMovement() {
        // Precondition: START station (checkInA) đang ở trạng thái PENDING
        checkInA.setStatus(CheckInStatus.PENDING);
        checkInA.setActualArrivalTime(null);

        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder()
                .latitude(10.7719)
                .longitude(106.6983)
                .stationId(1L)
                .stationName("Trạm A")
                .stopOrder(1)
                .build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder()
                .latitude(10.7750)
                .longitude(106.7000)
                .build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(1.0);

        // REV-001: Khẳng định rằng tại thời điểm geofencingService được gọi cho wp0, index của session vẫn là 0
        doAnswer(invocation -> {
            assertEquals(0, session.getCurrentWaypointIndex(),
                    "Waypoint index phải vẫn là 0 tại thời điểm đánh giá trạm START trước khi di chuyển");
            return Optional.of(checkInA);
        }).when(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp0.getLatitude()), eq(wp0.getLongitude()));

        simulatorService.tickSingleSimulation(session);

        // Đảm bảo geofencingService được gọi cho waypoint 0 (START) trước khi tới waypoint 1
        InOrder inOrder = inOrder(geofencingService);
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp0.getLatitude()), eq(wp0.getLongitude()));
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp1.getLatitude()), eq(wp1.getLongitude()));

        // Sau khi hoàn thành tick, index mới chuyển thành 1
        assertEquals(1, session.getCurrentWaypointIndex());
    }

    @Test
    @DisplayName("REV-001 / TC-005: Khi geofence START ném exception, session index không bị tăng trước và giữ nguyên tại 0")
    void tickSingleSimulation_WhenStartGeofenceThrowsException_SessionIndexRemainsAtZero() {
        checkInA.setStatus(CheckInStatus.PENDING);
        checkInA.setActualArrivalTime(null);

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder()
                .latitude(10.7719)
                .longitude(106.6983)
                .stationId(1L)
                .stationName("Trạm A")
                .stopOrder(1)
                .build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder()
                .latitude(10.7750)
                .longitude(106.7000)
                .build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(1.0);

        doThrow(new RuntimeException("Lỗi tạm thời khi kết nối database"))
                .when(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp0.getLatitude()), eq(wp0.getLongitude()));

        assertThrows(RuntimeException.class, () -> simulatorService.tickSingleSimulation(session));
        assertEquals(0, session.getCurrentWaypointIndex(),
                "Index không được tăng nếu bước kiểm tra trạm START bị ném ngoại lệ");
    }

    @Test
    @DisplayName("REV-003 / TC-009: Một session gặp geofence no-op/invalid không làm gián đoạn việc thực thi session tiếp theo")
    void tickSingleSimulation_SessionNoOpDoesNotBlockSubsequentSessionExecution() {
        // Session 1: geofencing trả về Optional.empty (no-op)
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle1 = Vehicle.builder().id(50L).plateNumber("51B-99999").status(VehicleStatus.IN_TRANSIT).currentSpeed(40.0).build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle1));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.7850).longitude(106.7050).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.7900).longitude(106.7100).build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(1.0);

        when(geofencingService.checkAndProcessAutoCheckIn(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty()); // No-op cho session 1

        // Tick session 1 thành công mà không có exception
        assertDoesNotThrow(() -> simulatorService.tickSingleSimulation(session));
        assertEquals(1, session.getCurrentWaypointIndex());

        // Session 2: Chuyến đi 600 độc lập
        TripCheckIn s2CheckIn = TripCheckIn.builder()
                .id(201L)
                .station(stationA)
                .stopOrder(1)
                .status(CheckInStatus.PENDING)
                .scheduledArrivalTime(fixedNow.plusMinutes(10))
                .build();
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(600L))
                .thenReturn(Collections.singletonList(s2CheckIn));

        Vehicle vehicle2 = Vehicle.builder().id(60L).plateNumber("51B-88888").status(VehicleStatus.IN_TRANSIT).currentSpeed(40.0).build();
        when(vehicleRepository.findById(60L)).thenReturn(Optional.of(vehicle2));

        SimulatorService.SimulationSession session2 = SimulatorService.SimulationSession.builder()
                .tripId(600L)
                .vehicleId(60L)
                .plateNumber("51B-88888")
                .routeId(2L)
                .routeName("Tuyến Số 2")
                .waypoints(Arrays.asList(wp0, wp1))
                .currentWaypointIndex(0)
                .baseSpeedKmh(40.0)
                .speedMultiplier(1.0)
                .isPaused(false)
                .isCompleted(false)
                .alertedIncidentIds(new HashSet<>())
                .build();

        // Tick session 2 sau session 1
        assertDoesNotThrow(() -> simulatorService.tickSingleSimulation(session2));
        assertEquals(1, session2.getCurrentWaypointIndex());

        // Cả 2 session đều phát telemetry độc lập
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/telemetry"), any(VehicleTelemetryDto.class));
    }

    @Test
    @DisplayName("TC-006: Multiplier cao làm bước nhảy qua nhiều waypoint phải kích hoạt geofence check cho mọi waypoint theo thứ tự")
    void tickSingleSimulation_MultiStepAdvance_ChecksAllIntermediateWaypointsInOrder() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        Vehicle vehicle = Vehicle.builder()
                .id(50L)
                .plateNumber("51B-99999")
                .status(VehicleStatus.IN_TRANSIT)
                .currentSpeed(40.0)
                .build();
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.7850).longitude(106.7050).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.7900).longitude(106.7100).build();
        SimulatorService.Waypoint wp2 = SimulatorService.Waypoint.builder().latitude(10.8000).longitude(106.7150).build();
        SimulatorService.Waypoint wp3 = SimulatorService.Waypoint.builder().latitude(10.8100).longitude(106.7200).build();

        session.setWaypoints(Arrays.asList(wp0, wp1, wp2, wp3));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(10.0); // Bước nhảy lớn đưa xe từ wp0 thẳng tới wp3

        simulatorService.tickSingleSimulation(session);

        assertEquals(3, session.getCurrentWaypointIndex());
        // Verify geofencingService được gọi lần lượt cho tất cả các waypoint 0, 1, 2, 3 theo thứ tự
        InOrder inOrder = inOrder(geofencingService);
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp0.getLatitude()), eq(wp0.getLongitude()));
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp1.getLatitude()), eq(wp1.getLongitude()));
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp2.getLatitude()), eq(wp2.getLongitude()));
        inOrder.verify(geofencingService).checkAndProcessAutoCheckIn(eq(500L), eq(wp3.getLatitude()), eq(wp3.getLongitude()));
    }

    // ==========================================
    // Feature 005 - Realtime Vehicle Simulator Tests
    // ==========================================

    @Test
    @DisplayName("TC-001: Start tạo session có simulationRunId UUID hợp lệ, trạng thái RUNNING và trùng lặp ném SimulatorConflictException")
    void startSimulation_CreatesValidSession_AndDuplicateStartThrowsConflict() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        SimulatorResponseDto res = simulatorService.startSimulation(500L);

        assertNotNull(res);
        assertEquals(500L, res.getTripId());
        assertEquals("RUNNING", res.getStatus());
        assertEquals(1.0, res.getMultiplier());
        assertEquals(0, res.getCurrentWaypointIndex());
        assertEquals(0, res.getLastPublishedSequence());
        assertNotNull(res.getSimulationRunId());
        assertDoesNotThrow(() -> UUID.fromString(res.getSimulationRunId()));

        // Duplicate start ném conflict
        SimulatorConflictException ex = assertThrows(SimulatorConflictException.class,
                () -> simulatorService.startSimulation(500L));
        assertTrue(ex.getMessage().contains("đã có phiên mô phỏng"));

        // Session ban đầu không bị thay đổi
        SimulatorResponseDto status = simulatorService.getStatus(500L);
        assertEquals("RUNNING", status.getStatus());
        assertEquals(res.getSimulationRunId(), status.getSimulationRunId());
    }

    @Test
    @DisplayName("TC-003: Pause/Resume giữ nguyên state, UUID, sequence và chuyển trạng thái sai ném conflict")
    void pauseAndResumeSimulation_RetainsStateUUIDAndSequence_AndInvalidTransitionsThrowConflict() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        SimulatorResponseDto startRes = simulatorService.startSimulation(500L);
        simulatorService.stopScheduler();
        String runId = startRes.getSimulationRunId();

        SimulatorService.SimulationSession activeSession = simulatorService.getSession(500L);
        activeSession.setCurrentWaypointIndex(4);
        activeSession.setLastPublishedSequence(3);
        activeSession.setSpeedMultiplier(2.0);

        // Pause
        SimulatorResponseDto pauseRes = simulatorService.pauseSimulation(500L);
        assertEquals("PAUSED", pauseRes.getStatus());
        assertEquals(runId, pauseRes.getSimulationRunId());
        assertEquals(4, pauseRes.getCurrentWaypointIndex());
        assertEquals(3, pauseRes.getLastPublishedSequence());
        assertEquals(2.0, pauseRes.getMultiplier());

        // Gọi Pause lần nữa khi đã PAUSED ném conflict
        assertThrows(SimulatorConflictException.class, () -> simulatorService.pauseSimulation(500L));

        // Resume
        SimulatorResponseDto resumeRes = simulatorService.resumeSimulation(500L);
        assertEquals("RUNNING", resumeRes.getStatus());
        assertEquals(runId, resumeRes.getSimulationRunId());
        assertEquals(4, resumeRes.getCurrentWaypointIndex());
        assertEquals(3, resumeRes.getLastPublishedSequence());
        assertEquals(2.0, resumeRes.getMultiplier());

        // Gọi Resume lần nữa khi đang RUNNING ném conflict
        assertThrows(SimulatorConflictException.class, () -> simulatorService.resumeSimulation(500L));
    }

    @Test
    @DisplayName("TC-004: Normal tick persist Vehicle IN_TRANSIT và phát telemetry có sequence tăng đơn điệu")
    void tickSingleSimulation_NormalTick_PersistsVehicleAndPublishesMonotonicSequence() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.7850).longitude(106.7050).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.7860).longitude(106.7060).build();
        SimulatorService.Waypoint wp2 = SimulatorService.Waypoint.builder().latitude(10.7870).longitude(106.7070).build();

        session.setWaypoints(Arrays.asList(wp0, wp1, wp2));
        session.setCurrentWaypointIndex(0);
        session.setLastPublishedSequence(0);
        String runId = session.getSimulationRunId();

        // Tick 1
        simulatorService.tickSingleSimulation(session);

        verify(vehicleRepository).save(argThat(v ->
                v.getStatus() == VehicleStatus.IN_TRANSIT &&
                v.getCurrentLatitude() == wp1.getLatitude() &&
                v.getCurrentLongitude() == wp1.getLongitude()
        ));

        ArgumentCaptor<VehicleTelemetryDto> captor1 = ArgumentCaptor.forClass(VehicleTelemetryDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/telemetry"), captor1.capture());
        VehicleTelemetryDto t1 = captor1.getValue();
        assertEquals(1, t1.getSequence());
        assertEquals(runId, t1.getSimulationRunId());
        assertEquals(VehicleStatus.IN_TRANSIT, t1.getStatus());
        assertEquals(TripStatus.RUNNING, t1.getTripStatus());

        // Tick 2
        simulatorService.tickSingleSimulation(session);
        ArgumentCaptor<VehicleTelemetryDto> captor2 = ArgumentCaptor.forClass(VehicleTelemetryDto.class);
        verify(messagingTemplate, atLeast(2)).convertAndSend(eq("/topic/telemetry"), captor2.capture());
        VehicleTelemetryDto t2 = captor2.getValue();
        assertEquals(2, t2.getSequence());
        assertEquals(runId, t2.getSimulationRunId());
    }

    @Test
    @DisplayName("TC-005: Phiên đang PAUSED trong tickAllSimulations không làm thay đổi vị trí xe và không phát telemetry")
    void tickAllSimulations_PausedSession_DoesNotMutateOrPublish() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        simulatorService.startSimulation(500L);
        simulatorService.pauseSimulation(500L);

        SimulatorService.SimulationSession activeSession = simulatorService.getSession(500L);
        int initialIndex = activeSession.getCurrentWaypointIndex();
        int initialSeq = activeSession.getLastPublishedSequence();

        clearInvocations(vehicleRepository, messagingTemplate, geofencingService);

        simulatorService.tickAllSimulations();

        assertEquals(initialIndex, activeSession.getCurrentWaypointIndex());
        assertEquals(initialSeq, activeSession.getLastPublishedSequence());
        verifyNoInteractions(vehicleRepository);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(geofencingService);
    }

    @Test
    @DisplayName("TC-006: Multiplier chỉ nhận 1, 2, 5, 10; giá trị ngoài whitelist ném 400 và không làm đổi multiplier hiện tại")
    void setSpeedMultiplier_WhitelistAccepted_InvalidThrowsAndRetainsMultiplier() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        simulatorService.startSimulation(500L);

        // Hợp lệ: 1.0, 2.0, 5.0, 10.0
        SimulatorResponseDto r2 = simulatorService.setSpeedMultiplier(500L, 2.0);
        assertEquals(2.0, r2.getMultiplier());

        SimulatorResponseDto r5 = simulatorService.setSpeedMultiplier(500L, 5.0);
        assertEquals(5.0, r5.getMultiplier());

        SimulatorResponseDto r10 = simulatorService.setSpeedMultiplier(500L, 10.0);
        assertEquals(10.0, r10.getMultiplier());

        SimulatorResponseDto r1 = simulatorService.setSpeedMultiplier(500L, 1.0);
        assertEquals(1.0, r1.getMultiplier());

        // Không hợp lệ: 0, -1, 10.1, NaN, Infinity, và near-whitelist (REV-005)
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, 0.0));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, -1.0));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, 10.1));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, 1.0000005));
        assertThrows(IllegalArgumentException.class, () -> simulatorService.setSpeedMultiplier(500L, 9.9999995));

        // Giá trị hiện tại vẫn giữ nguyên 1.0
        assertEquals(1.0, simulatorService.getStatus(500L).getMultiplier());

        // Đã hoàn thành (COMPLETED) ném conflict
        simulatorService.getSession(500L).setCompleted(true);
        assertThrows(SimulatorConflictException.class, () -> simulatorService.setSpeedMultiplier(500L, 2.0));
    }

    @Test
    @DisplayName("TC-007: Reset đặt lại toàn bộ check-in, Trip, Vehicle về START, xóa session khỏi map và cho phép Start run mới")
    void resetSimulation_ResetsTripAndCheckInsAndVehicle_RemovesSessionAndAllowsNewStartWithNewUUID() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        SimulatorResponseDto startRes = simulatorService.startSimulation(500L);
        String oldRunId = startRes.getSimulationRunId();
        SimulatorService.SimulationSession oldSession = simulatorService.getSession(500L);
        assertNotNull(oldSession);
        assertTrue(oldSession.isActive());

        // Giả lập trạng thái trip/check-in/vehicle bị thay đổi sau khi chạy
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEndTime(fixedNow);
        checkInA.setStatus(CheckInStatus.CHECKED_IN);
        checkInA.setActualArrivalTime(fixedNow.minusMinutes(5));
        vehicle.setStatus(VehicleStatus.IN_TRANSIT);
        vehicle.setCurrentLatitude(10.8500);
        vehicle.setCurrentLongitude(106.7800);
        vehicle.setCurrentSpeed(40.0);

        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));

        SimulatorResponseDto resetRes = simulatorService.resetSimulation(500L);

        assertNotNull(resetRes);
        assertEquals(500L, resetRes.getTripId());
        assertEquals("IDLE", resetRes.getStatus());
        assertEquals(1.0, resetRes.getMultiplier());

        // Kiểm tra Trip được reset về RUNNING, endTime null
        assertEquals(TripStatus.RUNNING, trip.getStatus());
        assertNull(trip.getEndTime());
        verify(tripRepository).save(trip);

        // Kiểm tra Check-in được reset về PENDING, actualArrivalTime null
        assertEquals(CheckInStatus.PENDING, checkInA.getStatus());
        assertNull(checkInA.getActualArrivalTime());
        verify(tripCheckInRepository).saveAll(anyList());

        // Kiểm tra Vehicle được reset về vị trí START, IDLE, speed 0
        assertEquals(stationA.getLatitude(), vehicle.getCurrentLatitude());
        assertEquals(stationA.getLongitude(), vehicle.getCurrentLongitude());
        assertEquals(0.0, vehicle.getCurrentSpeed());
        assertEquals(VehicleStatus.IDLE, vehicle.getStatus());
        verify(vehicleRepository).save(vehicle);

        // REV-001: Session cũ bị vô hiệu hóa và xóa khỏi map
        assertFalse(oldSession.isActive());
        assertNull(simulatorService.getSession(500L));
        assertEquals("IDLE", simulatorService.getStatus(500L).getStatus());

        // REV-001: Nếu scheduler tick chạy sau Reset với session reference cũ, tick bị skip hoàn toàn
        reset(vehicleRepository, messagingTemplate);
        simulatorService.tickAllSimulations();
        verifyNoInteractions(messagingTemplate);
        verify(vehicleRepository, never()).save(any());

        // Gọi Start lại sau reset tạo session mới với simulationRunId khác
        SimulatorResponseDto newStartRes = simulatorService.startSimulation(500L);
        assertNotEquals(oldRunId, newStartRes.getSimulationRunId());
        assertEquals("RUNNING", newStartRes.getStatus());

        // Reset khi không có session ném conflict
        Trip trip999 = Trip.builder().id(999L).build();
        when(tripRepository.findById(999L)).thenReturn(Optional.of(trip999));
        assertThrows(SimulatorConflictException.class, () -> simulatorService.resetSimulation(999L));
    }

    @Test
    @DisplayName("REV-001: Race giữa scheduler tick và Reset không làm phát ghost telemetry hay ghi đè DB")
    void resetSimulation_PreventsGhostTickAfterResetResponse() {
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));

        simulatorService.startSimulation(500L);
        simulatorService.stopScheduler();
        SimulatorService.SimulationSession capturedSession = simulatorService.getSession(500L);
        assertNotNull(capturedSession);
        assertTrue(capturedSession.isActive());

        // Reset chạy thành công
        simulatorService.resetSimulation(500L);
        assertFalse(capturedSession.isActive());
        assertNull(simulatorService.getSession(500L));

        // Giả lập scheduler trước đó đã giữ capturedSession và bây giờ vào tickAllSimulations
        reset(vehicleRepository, messagingTemplate);
        simulatorService.tickAllSimulations();

        // Không phát telemetry, không ghi đè DB
        verifyNoInteractions(messagingTemplate);
        verify(vehicleRepository, never()).save(any());
        assertEquals(stationA.getLatitude(), vehicle.getCurrentLatitude());
        assertEquals(stationA.getLongitude(), vehicle.getCurrentLongitude());
        assertEquals(VehicleStatus.IDLE, vehicle.getStatus());
    }

    @Test
    @DisplayName("TC-008: Phát cùng một payload telemetry với runId và sequence giống nhau tới cả 2 topic")
    void publishTelemetry_SendsIdenticalPayloadToBothTopics_WithMatchingRunIdAndSequence() {
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.7850).longitude(106.7050).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.7860).longitude(106.7060).build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setLastPublishedSequence(0);

        simulatorService.tickSingleSimulation(session);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<VehicleTelemetryDto> payloadCaptor = ArgumentCaptor.forClass(VehicleTelemetryDto.class);

        verify(messagingTemplate, times(2)).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        List<String> destinations = destinationCaptor.getAllValues();
        List<VehicleTelemetryDto> payloads = payloadCaptor.getAllValues();

        assertEquals(2, destinations.size());
        assertTrue(destinations.contains("/topic/telemetry"));
        assertTrue(destinations.contains("/topic/vehicle/50"));

        VehicleTelemetryDto p1 = payloads.get(0);
        VehicleTelemetryDto p2 = payloads.get(1);

        assertEquals(p1.getSimulationRunId(), p2.getSimulationRunId());
        assertEquals(p1.getSequence(), p2.getSequence());
        assertEquals(1, p1.getSequence());
        assertEquals(p1.getTripId(), p2.getTripId());
        assertEquals(p1.getVehicleId(), p2.getVehicleId());
        assertEquals(p1.getLatitude(), p2.getLatitude());
        assertEquals(p1.getLongitude(), p2.getLongitude());
        assertEquals(p1.getStatus(), p2.getStatus());
        assertEquals(p1.getTripStatus(), p2.getTripStatus());
    }

    @Test
    @DisplayName("TC-010: Terminal snapshot phát 1 lần và lỗi của session A không làm gián đoạn session B trong tickAllSimulations")
    void tickAllSimulations_TerminalEmitsOnce_AndErrorInSessionADoesNotBlockSessionB() {
        // Session A: hoàn thành terminal
        checkInA.setStatus(CheckInStatus.CHECKED_IN);
        checkInB.setStatus(CheckInStatus.CHECKED_IN);
        checkInC.setStatus(CheckInStatus.CHECKED_IN);

        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(vehicleRepository.findById(50L)).thenReturn(Optional.of(vehicle));

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.8650).longitude(106.8020).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.8659).longitude(106.8028).build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(5.0);

        // Terminal tick
        simulatorService.tickSingleSimulation(session);
        assertTrue(session.isCompleted());
        int terminalSeq = session.getLastPublishedSequence();

        // Tick lại khi đã completed -> early return, không tăng sequence hay phát telemetry
        reset(messagingTemplate);
        simulatorService.tickSingleSimulation(session);
        assertEquals(terminalSeq, session.getLastPublishedSequence());
        verifyNoInteractions(messagingTemplate);

        // Độc lập scheduler loop: Session A ném ngoại lệ, Session B vẫn chạy thành công
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(routeStationRepository.findByRouteIdOrderByStopOrderAsc(1L))
                .thenReturn(Arrays.asList(routeStationA, routeStationB, routeStationC));

        // Start session 500
        simulatorService.startSimulation(500L);
        simulatorService.stopScheduler();

        // Setup session 600
        Vehicle vehicle2 = Vehicle.builder().id(60L).plateNumber("51B-88888").status(VehicleStatus.IDLE).build();
        Trip trip2 = Trip.builder().id(600L).tripCode("TRIP-600").route(route).vehicle(vehicle2).status(TripStatus.RUNNING).build();
        when(tripRepository.findById(600L)).thenReturn(Optional.of(trip2));

        simulatorService.startSimulation(600L);
        simulatorService.stopScheduler();

        // Khi tick session 500, vehicleRepository ném RuntimeException
        when(vehicleRepository.findById(50L)).thenThrow(new RuntimeException("DB Connection failed for Vehicle 50"));
        when(vehicleRepository.findById(60L)).thenReturn(Optional.of(vehicle2));
        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(600L)).thenReturn(Collections.emptyList());

        reset(messagingTemplate);

        // tickAllSimulations không ném ngoại lệ dù session 500 bị lỗi
        assertDoesNotThrow(() -> simulatorService.tickAllSimulations());

        // Session 600 vẫn được phát telemetry
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/vehicle/60"), any(VehicleTelemetryDto.class));

        // REV-002: Session 500 gặp lỗi DB không được đánh dấu COMPLETED giả mạo
        assertFalse(simulatorService.getSession(500L).isCompleted());
        assertEquals("RUNNING", simulatorService.getStatus(500L).getStatus());
    }

    @Test
    @DisplayName("REV-002 / TC-010: Lỗi persistence tại terminal waypoint không được đánh dấu COMPLETED và không phát terminal telemetry")
    void tickSingleSimulation_TerminalPersistenceFailure_DoesNotMarkCompletedAndDoesNotPublishTerminalTelemetry() {
        checkInA.setStatus(CheckInStatus.CHECKED_IN);
        checkInB.setStatus(CheckInStatus.CHECKED_IN);
        checkInC.setStatus(CheckInStatus.CHECKED_IN);

        when(tripCheckInRepository.findByTripIdOrderByStopOrderAsc(500L))
                .thenReturn(Arrays.asList(checkInA, checkInB, checkInC));
        when(incidentRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        SimulatorService.Waypoint wp0 = SimulatorService.Waypoint.builder().latitude(10.8650).longitude(106.8020).build();
        SimulatorService.Waypoint wp1 = SimulatorService.Waypoint.builder().latitude(10.8659).longitude(106.8028).build();

        session.setWaypoints(Arrays.asList(wp0, wp1));
        session.setCurrentWaypointIndex(0);
        session.setSpeedMultiplier(5.0);

        // Giả lập lỗi DB khi lưu Vehicle tại terminal waypoint
        when(vehicleRepository.findById(50L)).thenThrow(new RuntimeException("DB Connection failed for Vehicle 50"));

        assertThrows(RuntimeException.class, () -> simulatorService.tickSingleSimulation(session));

        // REV-002: Không được đánh dấu isCompleted = true
        assertFalse(session.isCompleted());
        // Không được phát terminal telemetry
        verifyNoInteractions(messagingTemplate);
    }
}
