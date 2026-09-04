package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.StationEtaDto;
import com.quangkhai.vehiceltracking_backend.dto.VehicleTelemetryDto;
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
                .build();
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
}
