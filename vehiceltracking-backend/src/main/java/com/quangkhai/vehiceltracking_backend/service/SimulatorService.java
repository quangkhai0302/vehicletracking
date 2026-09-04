package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.AlertMessageDto;
import com.quangkhai.vehiceltracking_backend.dto.SimulatorResponseDto;
import com.quangkhai.vehiceltracking_backend.dto.StationEtaDto;
import com.quangkhai.vehiceltracking_backend.dto.VehicleTelemetryDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
import com.quangkhai.vehiceltracking_backend.exception.SimulatorConflictException;
import com.quangkhai.vehiceltracking_backend.exception.SimulatorNotFoundException;
import com.quangkhai.vehiceltracking_backend.repository.*;
import com.quangkhai.vehiceltracking_backend.util.GeoUtil;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorService {

    private static final Set<Double> ALLOWED_MULTIPLIERS = Set.of(1.0, 2.0, 5.0, 10.0);

    private final TripRepository tripRepository;
    private final TripCheckInRepository tripCheckInRepository;
    private final RouteStationRepository routeStationRepository;
    private final VehicleRepository vehicleRepository;
    private final TrafficIncidentRepository incidentRepository;
    private final GeofencingService geofencingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    private final Map<Long, SimulationSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> simulationTask;

    @Data
    @Builder
    @AllArgsConstructor
    public static class Waypoint {
        private double latitude;
        private double longitude;
        private Long stationId; // ID trạm nếu đây là điểm dừng của trạm
        private String stationName;
        private Integer stopOrder;
    }

    @Data
    @Builder
    public static class SimulationSession {
        private Long tripId;
        private Long vehicleId;
        private String plateNumber;
        private Long routeId;
        private String routeName;
        private List<Waypoint> waypoints;
        private int currentWaypointIndex;
        private double baseSpeedKmh; // Tốc độ cơ sở (km/h)
        private double speedMultiplier; // Hệ số tua nhanh (1x, 2x, 5x...)
        private boolean isPaused;
        private boolean isCompleted;
        @Builder.Default
        private volatile boolean active = true;
        private Set<Long> alertedIncidentIds; // Tránh bắn trùng lặp alert cho cùng 1 sự cố
        private String simulationRunId; // UUID của phiên chạy
        private int lastPublishedSequence; // Sequence tăng dần strictly monotonic trong 1 run

        public String getPublicStatus() {
            if (isCompleted) {
                return "COMPLETED";
            } else if (isPaused) {
                return "PAUSED";
            } else {
                return "RUNNING";
            }
        }
    }

    public SimulatorResponseDto startSimulation(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession existing = activeSessions.get(tripId);
        if (existing != null) {
            throw new SimulatorConflictException("Chuyến đi " + tripId + " đã có phiên mô phỏng (trạng thái: " + existing.getPublicStatus() + ")");
        }

        List<RouteStation> routeStations = routeStationRepository
                .findByRouteIdOrderByStopOrderAsc(trip.getRoute().getId());

        if (routeStations.size() < 2) {
            throw new IllegalArgumentException("Tuyến đường phải có ít nhất 2 trạm dừng để giả lập");
        }

        // Tạo chuỗi điểm chi tiết (waypoints) nối giữa các trạm
        List<Waypoint> waypoints = generateDetailedWaypoints(routeStations);
        String simulationRunId = UUID.randomUUID().toString();

        SimulationSession session = SimulationSession.builder()
                .tripId(tripId)
                .vehicleId(trip.getVehicle().getId())
                .plateNumber(trip.getVehicle().getPlateNumber())
                .routeId(trip.getRoute().getId())
                .routeName(trip.getRoute().getName())
                .waypoints(waypoints)
                .currentWaypointIndex(0)
                .baseSpeedKmh(40.0) // 40 km/h
                .speedMultiplier(1.0)
                .isPaused(false)
                .isCompleted(false)
                .alertedIncidentIds(new HashSet<>())
                .simulationRunId(simulationRunId)
                .lastPublishedSequence(0)
                .build();

        SimulationSession prev = activeSessions.putIfAbsent(tripId, session);
        if (prev != null) {
            throw new SimulatorConflictException("Chuyến đi " + tripId + " đã có phiên mô phỏng (trạng thái: " + prev.getPublicStatus() + ")");
        }

        // Đảm bảo task tick đang chạy
        ensureSchedulerRunning();

        log.info("Khởi động Simulator cho chuyến đi {} (xe {}, runId: {}) với {} waypoints",
                trip.getTripCode(), trip.getVehicle().getPlateNumber(), simulationRunId, waypoints.size());

        return buildResponseDto(session, "Simulator đã bắt đầu cho chuyến đi: " + tripId);
    }

    public SimulatorResponseDto pauseSimulation(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession session = activeSessions.get(tripId);
        if (session == null) {
            throw new SimulatorConflictException("Không tìm thấy phiên mô phỏng đang hoạt động cho chuyến đi: " + tripId);
        }

        synchronized (session) {
            if (session.isCompleted()) {
                throw new SimulatorConflictException("Không thể tạm dừng phiên mô phỏng đã hoàn thành");
            }
            if (session.isPaused()) {
                throw new SimulatorConflictException("Phiên mô phỏng đã ở trạng thái tạm dừng");
            }
            session.setPaused(true);
            log.info("Đã tạm dừng mô phỏng chuyến đi {} (runId: {})", tripId, session.getSimulationRunId());
            return buildResponseDto(session, "Đã tạm dừng mô phỏng");
        }
    }

    public SimulatorResponseDto resumeSimulation(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession session = activeSessions.get(tripId);
        if (session == null) {
            throw new SimulatorConflictException("Không tìm thấy phiên mô phỏng đang hoạt động cho chuyến đi: " + tripId);
        }

        synchronized (session) {
            if (session.isCompleted()) {
                throw new SimulatorConflictException("Không thể tiếp tục phiên mô phỏng đã hoàn thành");
            }
            if (!session.isPaused()) {
                throw new SimulatorConflictException("Phiên mô phỏng đang chạy, không thể tiếp tục");
            }
            session.setPaused(false);
            log.info("Đã tiếp tục mô phỏng chuyến đi {} (runId: {})", tripId, session.getSimulationRunId());
            return buildResponseDto(session, "Đã tiếp tục mô phỏng");
        }
    }

    @Transactional
    public SimulatorResponseDto resetSimulation(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession session = activeSessions.get(tripId);
        if (session == null) {
            throw new SimulatorConflictException("Không có phiên mô phỏng nào đang chạy hoặc cần đặt lại cho chuyến đi: " + tripId);
        }

        // 1. Xác định first RouteStation ordered; không có start station là 400
        List<RouteStation> routeStations = routeStationRepository.findByRouteIdOrderByStopOrderAsc(trip.getRoute().getId());
        if (routeStations.isEmpty() || routeStations.get(0).getStation() == null) {
            throw new IllegalArgumentException("Không tìm thấy trạm xuất phát hợp lệ để đặt lại mô phỏng");
        }
        Station startStation = routeStations.get(0).getStation();

        synchronized (session) {
            // REV-001: Vô hiệu hóa ngay lập tức để ngăn scheduler tick run cũ sau Reset
            session.setActive(false);

            // 2. Reset toàn bộ check-in Trip: status=PENDING, actualArrivalTime=null
            List<TripCheckIn> checkIns = tripCheckInRepository.findByTripIdOrderByStopOrderAsc(tripId);
            for (TripCheckIn ci : checkIns) {
                ci.setStatus(CheckInStatus.PENDING);
                ci.setActualArrivalTime(null);
            }
            tripCheckInRepository.saveAll(checkIns);

            // 3. Set Trip status=RUNNING, endTime=null
            trip.setStatus(TripStatus.RUNNING);
            trip.setEndTime(null);
            tripRepository.save(trip);

            // 4. Set Vehicle location = START coordinates, speed 0.0, heading 0.0, status IDLE
            Vehicle vehicle = trip.getVehicle();
            if (vehicle != null) {
                vehicle.setCurrentLatitude(startStation.getLatitude());
                vehicle.setCurrentLongitude(startStation.getLongitude());
                vehicle.setCurrentSpeed(0.0);
                vehicle.setCurrentHeading(0.0);
                vehicle.setStatus(VehicleStatus.IDLE);
                vehicle.setLastUpdatedAt(LocalDateTime.now(clock));
                vehicleRepository.save(vehicle);
            }

            // 5. Remove session from activeSessions sau khi persist DB thành công
            activeSessions.remove(tripId, session);
        }

        log.info("Đã đặt lại trạng thái mô phỏng cho chuyến đi {}", tripId);
        return SimulatorResponseDto.builder()
                .tripId(tripId)
                .status("IDLE")
                .message("Đã đặt lại trạng thái ban đầu")
                .multiplier(1.0)
                .build();
    }

    public SimulatorResponseDto setSpeedMultiplier(Long tripId, double multiplier) {
        validateMultiplier(multiplier);

        tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession session = activeSessions.get(tripId);
        if (session == null) {
            throw new SimulatorConflictException("Không tìm thấy phiên mô phỏng cho chuyến đi: " + tripId);
        }

        synchronized (session) {
            if (session.isCompleted()) {
                throw new SimulatorConflictException("Không thể thay đổi tốc độ của phiên mô phỏng đã hoàn thành");
            }
            session.setSpeedMultiplier(multiplier);
            log.info("Đã cập nhật hệ số tốc độ cho chuyến đi {} (runId: {}) thành {}x",
                    tripId, session.getSimulationRunId(), multiplier);
            return buildResponseDto(session, "Đã cập nhật hệ số tốc độ");
        }
    }

    public SimulatorResponseDto getStatus(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new SimulatorNotFoundException("Không tìm thấy chuyến đi ID: " + tripId));

        SimulationSession session = activeSessions.get(tripId);
        if (session == null) {
            return SimulatorResponseDto.builder()
                    .tripId(tripId)
                    .status("IDLE")
                    .multiplier(1.0)
                    .build();
        }
        return buildResponseDto(session, null);
    }

    public SimulationSession getSession(Long tripId) {
        return activeSessions.get(tripId);
    }

    public SimulatorResponseDto buildResponseDto(SimulationSession session, String message) {
        if (session == null) {
            return SimulatorResponseDto.builder()
                    .status("IDLE")
                    .multiplier(1.0)
                    .message(message)
                    .build();
        }
        return SimulatorResponseDto.builder()
                .tripId(session.getTripId())
                .status(session.getPublicStatus())
                .simulationRunId(session.getSimulationRunId())
                .multiplier(session.getSpeedMultiplier())
                .currentWaypointIndex(session.getCurrentWaypointIndex())
                .lastPublishedSequence(session.getLastPublishedSequence())
                .message(message)
                .build();
    }

    private void validateMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            throw new IllegalArgumentException("Hệ số tốc độ không hợp lệ. Chỉ chấp nhận các giá trị: 1, 2, 5, 10");
        }
        // REV-005: Kiểm tra membership exact, không dùng epsilon tolerance
        if (!ALLOWED_MULTIPLIERS.contains(multiplier)) {
            throw new IllegalArgumentException("Hệ số tốc độ không hợp lệ. Chỉ chấp nhận các giá trị: 1, 2, 5, 10");
        }
    }

    private synchronized void ensureSchedulerRunning() {
        if (simulationTask == null || simulationTask.isCancelled() || simulationTask.isDone()) {
            simulationTask = scheduler.scheduleAtFixedRate(this::tickAllSimulations, 1000, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stopScheduler() {
        if (simulationTask != null) {
            simulationTask.cancel(true);
            simulationTask = null;
        }
    }

    /**
     * Vòng lặp mô phỏng mỗi 1 giây (1000ms)
     * BR-007: try/catch quanh từng session để lỗi session A không chặn session B
     */
    void tickAllSimulations() {
        for (SimulationSession session : activeSessions.values()) {
            try {
                synchronized (session) {
                    // REV-001: Kiểm tra session còn active và map entry vẫn chính là session instance này
                    if (!session.isActive() || session.isPaused() || session.isCompleted()
                            || activeSessions.get(session.getTripId()) != session) {
                        continue;
                    }
                    tickSingleSimulation(session);
                }
            } catch (Exception e) {
                log.error("Lỗi trong vòng lặp giả lập cho chuyến đi {} (runId: {}): ",
                        session.getTripId(), session.getSimulationRunId(), e);
            }
        }
    }

    void tickSingleSimulation(SimulationSession session) {
        if (session.isCompleted()) {
            return;
        }

        List<Waypoint> waypoints = session.getWaypoints();
        if (waypoints == null || waypoints.isEmpty()) {
            return;
        }

        int currentIndex = Math.min(session.getCurrentWaypointIndex(), waypoints.size() - 1);
        Waypoint currentWp = waypoints.get(currentIndex);
        Waypoint nextWp = (currentIndex < waypoints.size() - 1) ? waypoints.get(currentIndex + 1) : currentWp;

        // 1. Tính góc xoay (heading)
        double heading = (currentIndex < waypoints.size() - 1)
                ? GeoUtil.calculateBearing(
                        currentWp.getLatitude(), currentWp.getLongitude(),
                        nextWp.getLatitude(), nextWp.getLongitude()
                )
                : 0.0;

        // 2. Kiểm tra sự cố giao thông (kẹt xe, tai nạn, công trường)
        List<TrafficIncident> activeIncidents = incidentRepository.findByActiveTrue();
        boolean inIncidentZone = false;
        double speedFactor = 1.0;
        String incidentNotice = null;

        for (TrafficIncident incident : activeIncidents) {
            double distToIncident = GeoUtil.calculateDistanceMeters(
                    currentWp.getLatitude(), currentWp.getLongitude(),
                    incident.getLatitude(), incident.getLongitude()
            );

            if (distToIncident <= incident.getRadiusMeters()) {
                inIncidentZone = true;
                double reduction = incident.getSpeedReductionPercent() != null ? incident.getSpeedReductionPercent() : 60.0;
                double thisFactor = Math.max(0.1, (100.0 - reduction) / 100.0);
                speedFactor = Math.min(speedFactor, thisFactor);
                incidentNotice = incident.getType().name() + ": " + incident.getTitle();

                // Nếu chưa gửi cảnh báo về sự cố này cho chuyến đi, phát cảnh báo ngay
                if (!session.getAlertedIncidentIds().contains(incident.getId())) {
                    session.getAlertedIncidentIds().add(incident.getId());

                    AlertMessageDto alert = AlertMessageDto.builder()
                            .id(UUID.randomUUID().toString())
                            .level("DANGER")
                            .title("Cảnh báo thay đổi lịch trình!")
                            .message("Xe [" + session.getPlateNumber() + "] đã tiến vào khu vực [" + incident.getTitle() +
                                    "]. Tốc độ bị giảm " + Math.round(reduction) + "%, dự kiến giờ đến trạm tiếp theo sẽ bị trễ!")
                            .tripId(session.getTripId())
                            .vehicleId(session.getVehicleId())
                            .incidentId(incident.getId())
                            .timestamp(LocalDateTime.now(clock))
                            .build();

                    messagingTemplate.convertAndSend("/topic/alerts", alert);
                }
            }
        }

        // Tính tốc độ thực tế (km/h)
        double effectiveSpeedKmh = session.getBaseSpeedKmh() * speedFactor;

        // 3. Tiến hành bước tiếp theo theo tốc độ và multiplier
        double metersPerSecond = (effectiveSpeedKmh * 1000.0 / 3600.0) * session.getSpeedMultiplier();
        int stepAdvance = Math.max(1, (int) Math.round(metersPerSecond / 15.0));
        int nextIndex = Math.min(currentIndex + stepAdvance, waypoints.size() - 1);

        // 4. Kiểm tra Geofencing tự động check-in
        // SPEC-003 / BR-004: Khi bắt đầu ở waypoint index 0, kiểm tra vị trí hiện tại (START) trước khi tăng index di chuyển
        if (currentIndex == 0) {
            geofencingService.checkAndProcessAutoCheckIn(
                    session.getTripId(),
                    currentWp.getLatitude(),
                    currentWp.getLongitude()
            );
        }

        // Cập nhật waypoint index của session sau khi START đã được xử lý
        session.setCurrentWaypointIndex(nextIndex);

        // Sau đó kiểm tra tất cả các waypoint trong bước nhảy (currentIndex, nextIndex]
        if (currentIndex < nextIndex) {
            for (int i = currentIndex + 1; i <= nextIndex; i++) {
                Waypoint wp = waypoints.get(i);
                geofencingService.checkAndProcessAutoCheckIn(
                        session.getTripId(),
                        wp.getLatitude(),
                        wp.getLongitude()
                );
            }
        } else if (currentIndex > 0) {
            Waypoint wp = waypoints.get(nextIndex);
            geofencingService.checkAndProcessAutoCheckIn(
                    session.getTripId(),
                    wp.getLatitude(),
                    wp.getLongitude()
            );
        }

        Waypoint newPos = waypoints.get(nextIndex);

        // 5. Tính ETA đến các trạm còn lại trong lịch trình
        List<StationEtaDto> stationEtas = calculateEtas(session, newPos, effectiveSpeedKmh);

        boolean allCheckedIn = !stationEtas.isEmpty() && stationEtas.stream().allMatch(eta -> eta.getStatus() == CheckInStatus.CHECKED_IN);
        boolean isTerminal = allCheckedIn;

        StationEtaDto finalEta = stationEtas.isEmpty() ? null : stationEtas.get(stationEtas.size() - 1);

        if (isTerminal) {
            LocalDateTime completionTime = (finalEta != null && finalEta.getEstimatedArrivalTime() != null)
                    ? finalEta.getEstimatedArrivalTime()
                    : LocalDateTime.now(clock);

            // Cập nhật vị trí xe
            vehicleRepository.findById(session.getVehicleId()).ifPresent(v -> {
                v.setCurrentLatitude(newPos.getLatitude());
                v.setCurrentLongitude(newPos.getLongitude());
                v.setCurrentSpeed(0.0);
                v.setCurrentHeading(round(heading, 1));
                v.setStatus(VehicleStatus.IDLE);
                v.setLastUpdatedAt(LocalDateTime.now(clock));
                vehicleRepository.save(v);
            });

            publishTelemetry(
                    session,
                    newPos,
                    0.0,
                    heading,
                    VehicleStatus.IDLE,
                    TripStatus.COMPLETED,
                    nextIndex,
                    null,
                    "Đã hoàn thành",
                    0.0,
                    0L,
                    0L,
                    completionTime,
                    stationEtas,
                    inIncidentZone,
                    incidentNotice
            );

            // REV-002: Đặt completed CHỈ SAU KHI lưu xe và phát telemetry thành công
            session.setCompleted(true);
            log.info("Chuyến đi {} (runId: {}) đã hoàn thành mô phỏng", session.getTripId(), session.getSimulationRunId());
        } else {
            // Cập nhật vị trí xe
            vehicleRepository.findById(session.getVehicleId()).ifPresent(v -> {
                v.setCurrentLatitude(newPos.getLatitude());
                v.setCurrentLongitude(newPos.getLongitude());
                v.setCurrentSpeed(round(effectiveSpeedKmh, 1));
                v.setCurrentHeading(round(heading, 1));
                v.setStatus(VehicleStatus.IN_TRANSIT);
                v.setLastUpdatedAt(LocalDateTime.now(clock));
                vehicleRepository.save(v);
            });

            StationEtaDto nextTarget = stationEtas.stream()
                    .filter(eta -> eta.getStatus() == CheckInStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            Long etaSecondsToCompletion = (finalEta != null) ? finalEta.getEtaSeconds() : 0L;
            LocalDateTime estimatedCompletionTime = (finalEta != null) ? finalEta.getEstimatedArrivalTime() : LocalDateTime.now(clock);

            publishTelemetry(
                    session,
                    newPos,
                    effectiveSpeedKmh,
                    heading,
                    VehicleStatus.IN_TRANSIT,
                    TripStatus.RUNNING,
                    nextIndex,
                    nextTarget != null ? nextTarget.getStationId() : null,
                    nextTarget != null ? nextTarget.getStationName() : "Đã hoàn thành",
                    nextTarget != null ? nextTarget.getDistanceRemainingMeters() : 0.0,
                    nextTarget != null ? nextTarget.getEtaSeconds() : 0L,
                    etaSecondsToCompletion,
                    estimatedCompletionTime,
                    stationEtas,
                    inIncidentZone,
                    incidentNotice
            );
        }
    }

    /**
     * Helper duy nhất để publish telemetry snapshot tới cả 2 topic
     * BR-005: Tăng sequence 1 lần và gửi cùng snapshot tới /topic/telemetry và /topic/vehicle/{vehicleId}
     */
    private VehicleTelemetryDto publishTelemetry(SimulationSession session,
                                                Waypoint newPos,
                                                double speedKmh,
                                                double heading,
                                                VehicleStatus vehicleStatus,
                                                TripStatus tripStatus,
                                                int currentStopIndex,
                                                Long targetStationId,
                                                String targetStationName,
                                                Double distanceToTargetMeters,
                                                Long etaSecondsToTarget,
                                                Long etaSecondsToCompletion,
                                                LocalDateTime estimatedCompletionTime,
                                                List<StationEtaDto> stationEtas,
                                                boolean inIncidentZone,
                                                String currentIncidentNotice) {
        session.setLastPublishedSequence(session.getLastPublishedSequence() + 1);
        LocalDateTime now = LocalDateTime.now(clock);

        VehicleTelemetryDto telemetry = VehicleTelemetryDto.builder()
                .simulationRunId(session.getSimulationRunId())
                .sequence(session.getLastPublishedSequence())
                .vehicleId(session.getVehicleId())
                .plateNumber(session.getPlateNumber())
                .tripId(session.getTripId())
                .tripCode("TRIP-" + session.getTripId())
                .tripStatus(tripStatus)
                .routeId(session.getRouteId())
                .routeName(session.getRouteName())
                .latitude(newPos.getLatitude())
                .longitude(newPos.getLongitude())
                .speed(round(speedKmh, 1))
                .heading(round(heading, 1))
                .status(vehicleStatus)
                .currentStopIndex(currentStopIndex)
                .targetStationId(targetStationId)
                .targetStationName(targetStationName)
                .distanceToTargetMeters(distanceToTargetMeters)
                .etaSecondsToTarget(etaSecondsToTarget)
                .etaSecondsToCompletion(etaSecondsToCompletion)
                .estimatedCompletionTime(estimatedCompletionTime)
                .stationsEta(stationEtas)
                .inIncidentZone(inIncidentZone)
                .currentIncidentNotice(currentIncidentNotice)
                .timestamp(now)
                .build();

        messagingTemplate.convertAndSend("/topic/telemetry", telemetry);
        messagingTemplate.convertAndSend("/topic/vehicle/" + session.getVehicleId(), telemetry);
        return telemetry;
    }

    /**
     * Tính toán ETA động cho tất cả các trạm trong chuyến dựa trên vận tốc hiện thời
     */
    List<StationEtaDto> calculateEtas(SimulationSession session, Waypoint currentPos, double effectiveSpeedKmh) {
        List<TripCheckIn> checkIns = tripCheckInRepository.findByTripIdOrderByStopOrderAsc(session.getTripId());
        List<StationEtaDto> result = new ArrayList<>();

        double speedMps = Math.max(1.0, (effectiveSpeedKmh * 1000.0) / 3600.0);
        double accumulatedDistanceMeters = 0.0;
        double previousLat = currentPos.getLatitude();
        double previousLng = currentPos.getLongitude();

        for (TripCheckIn ci : checkIns) {
            Station station = ci.getStation();

            if (ci.getStatus() == CheckInStatus.CHECKED_IN) {
                result.add(StationEtaDto.builder()
                        .stationId(station.getId())
                        .stationName(station.getName())
                        .stationCode(station.getCode())
                        .stopOrder(ci.getStopOrder())
                        .distanceRemainingMeters(0.0)
                        .etaSeconds(0L)
                        .estimatedArrivalTime(ci.getActualArrivalTime())
                        .status(CheckInStatus.CHECKED_IN)
                        .build());
                continue;
            }

            // Tính khoảng cách từ điểm hiện tại đến trạm này
            double distToStation = GeoUtil.calculateDistanceMeters(
                    previousLat, previousLng,
                    station.getLatitude(), station.getLongitude()
            );
            accumulatedDistanceMeters += distToStation;

            long etaSeconds = Math.max(0L, Math.round(accumulatedDistanceMeters / speedMps));
            LocalDateTime estimatedTime = LocalDateTime.now(clock).plusSeconds(etaSeconds);

            result.add(StationEtaDto.builder()
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .stationCode(station.getCode())
                    .stopOrder(ci.getStopOrder())
                    .distanceRemainingMeters(round(accumulatedDistanceMeters, 1))
                    .etaSeconds(etaSeconds)
                    .estimatedArrivalTime(estimatedTime)
                    .status(CheckInStatus.PENDING)
                    .build());

            previousLat = station.getLatitude();
            previousLng = station.getLongitude();
        }

        return result;
    }

    /**
     * Sinh tập hợp các điểm waypoints dày đặc nối các trạm (khoảng 15-20 mét 1 điểm) để xe di chuyển mượt mà
     */
    private List<Waypoint> generateDetailedWaypoints(List<RouteStation> routeStations) {
        List<Waypoint> waypoints = new ArrayList<>();
        final double STEP_METERS = 20.0; // Khoảng cách giữa 2 điểm nội suy

        for (int i = 0; i < routeStations.size(); i++) {
            RouteStation current = routeStations.get(i);
            Station startStation = current.getStation();

            // Thêm điểm tại chính trạm
            waypoints.add(Waypoint.builder()
                    .latitude(startStation.getLatitude())
                    .longitude(startStation.getLongitude())
                    .stationId(startStation.getId())
                    .stationName(startStation.getName())
                    .stopOrder(current.getStopOrder())
                    .build());

            if (i < routeStations.size() - 1) {
                RouteStation next = routeStations.get(i + 1);
                Station endStation = next.getStation();

                double segmentDistance = GeoUtil.calculateDistanceMeters(
                        startStation.getLatitude(), startStation.getLongitude(),
                        endStation.getLatitude(), endStation.getLongitude()
                );

                int steps = Math.max(1, (int) Math.round(segmentDistance / STEP_METERS));

                for (int s = 1; s < steps; s++) {
                    double fraction = (double) s / steps;
                    double[] coords = GeoUtil.interpolate(
                            startStation.getLatitude(), startStation.getLongitude(),
                            endStation.getLatitude(), endStation.getLongitude(),
                            fraction
                    );
                    waypoints.add(Waypoint.builder()
                            .latitude(coords[0])
                            .longitude(coords[1])
                            .build());
                }
            }
        }

        return waypoints;
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    @PreDestroy
    public void cleanup() {
        if (simulationTask != null) {
            simulationTask.cancel(true);
        }
        scheduler.shutdown();
    }
}
