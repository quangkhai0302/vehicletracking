package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.AlertMessageDto;
import com.quangkhai.vehiceltracking_backend.dto.StationEtaDto;
import com.quangkhai.vehiceltracking_backend.dto.VehicleTelemetryDto;
import com.quangkhai.vehiceltracking_backend.entity.*;
import com.quangkhai.vehiceltracking_backend.enums.CheckInStatus;
import com.quangkhai.vehiceltracking_backend.enums.TripStatus;
import com.quangkhai.vehiceltracking_backend.enums.VehicleStatus;
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
        private Set<Long> alertedIncidentIds; // Tránh bắn trùng lặp alert cho cùng 1 sự cố
    }

    public synchronized void startSimulation(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến đi ID: " + tripId));

        List<RouteStation> routeStations = routeStationRepository
                .findByRouteIdOrderByStopOrderAsc(trip.getRoute().getId());

        if (routeStations.size() < 2) {
            throw new IllegalArgumentException("Tuyến đường phải có ít nhất 2 trạm dừng để giả lập");
        }

        // Tạo chuỗi điểm chi tiết (waypoints) nối giữa các trạm
        List<Waypoint> waypoints = generateDetailedWaypoints(routeStations);

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
                .build();

        activeSessions.put(tripId, session);

        // Đảm bảo task tick đang chạy
        ensureSchedulerRunning();

        log.info("Khởi động Simulator cho chuyến đi {} (xe {}) với {} waypoints",
                trip.getTripCode(), trip.getVehicle().getPlateNumber(), waypoints.size());
    }

    public void pauseSimulation(Long tripId) {
        SimulationSession session = activeSessions.get(tripId);
        if (session != null) {
            session.setPaused(true);
        }
    }

    public void resumeSimulation(Long tripId) {
        SimulationSession session = activeSessions.get(tripId);
        if (session != null) {
            session.setPaused(false);
        }
    }

    public void resetSimulation(Long tripId) {
        SimulationSession session = activeSessions.get(tripId);
        if (session != null) {
            session.setCurrentWaypointIndex(0);
            session.setCompleted(false);
            session.setPaused(false);
            session.getAlertedIncidentIds().clear();

            // Đặt lại trạng thái PENDING cho các checkin
            List<TripCheckIn> checkIns = tripCheckInRepository.findByTripIdOrderByStopOrderAsc(tripId);
            for (TripCheckIn ci : checkIns) {
                ci.setStatus(CheckInStatus.PENDING);
                ci.setActualArrivalTime(null);
            }
            tripCheckInRepository.saveAll(checkIns);
        }
    }

    public void setSpeedMultiplier(Long tripId, double multiplier) {
        SimulationSession session = activeSessions.get(tripId);
        if (session != null && multiplier > 0) {
            session.setSpeedMultiplier(multiplier);
        }
    }

    public SimulationSession getSession(Long tripId) {
        return activeSessions.get(tripId);
    }

    private synchronized void ensureSchedulerRunning() {
        if (simulationTask == null || simulationTask.isCancelled() || simulationTask.isDone()) {
            simulationTask = scheduler.scheduleAtFixedRate(this::tickAllSimulations, 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Vòng lặp mô phỏng mỗi 1 giây (1000ms)
     */
    private void tickAllSimulations() {
        try {
            for (SimulationSession session : activeSessions.values()) {
                if (session.isPaused() || session.isCompleted()) {
                    continue;
                }
                tickSingleSimulation(session);
            }
        } catch (Exception e) {
            log.error("Lỗi trong vòng lặp giả lập: ", e);
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
        // Khoảng cách mỗi bước waypoint khoảng 20m, tính số bước tiến lên
        double metersPerSecond = (effectiveSpeedKmh * 1000.0 / 3600.0) * session.getSpeedMultiplier();
        int stepAdvance = Math.max(1, (int) Math.round(metersPerSecond / 15.0));
        int nextIndex = Math.min(currentIndex + stepAdvance, waypoints.size() - 1);
        session.setCurrentWaypointIndex(nextIndex);

        // 4. Kiểm tra Geofencing tự động check-in cho tất cả các waypoint trong bước nhảy
        if (currentIndex < nextIndex) {
            for (int i = currentIndex + 1; i <= nextIndex; i++) {
                Waypoint wp = waypoints.get(i);
                geofencingService.checkAndProcessAutoCheckIn(
                        session.getTripId(),
                        wp.getLatitude(),
                        wp.getLongitude()
                );
            }
        } else {
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

        // BR-004 / REV-001: Chỉ kết thúc mô phỏng khi TẤT CẢ check-in đã CHECKED_IN
        boolean allCheckedIn = !stationEtas.isEmpty() && stationEtas.stream().allMatch(eta -> eta.getStatus() == CheckInStatus.CHECKED_IN);
        boolean isTerminal = allCheckedIn;

        StationEtaDto finalEta = stationEtas.isEmpty() ? null : stationEtas.get(stationEtas.size() - 1);

        if (isTerminal) {
            session.setCompleted(true);

            LocalDateTime completionTime = (finalEta != null && finalEta.getEstimatedArrivalTime() != null)
                    ? finalEta.getEstimatedArrivalTime()
                    : LocalDateTime.now(clock);

            // Cập nhật vị trí xe
            try {
                vehicleRepository.findById(session.getVehicleId()).ifPresent(v -> {
                    v.setCurrentLatitude(newPos.getLatitude());
                    v.setCurrentLongitude(newPos.getLongitude());
                    v.setCurrentSpeed(0.0);
                    v.setCurrentHeading(round(heading, 1));
                    v.setStatus(VehicleStatus.IDLE);
                    v.setLastUpdatedAt(LocalDateTime.now(clock));
                    vehicleRepository.save(v);
                });
            } catch (Exception ignored) {}

            VehicleTelemetryDto telemetry = VehicleTelemetryDto.builder()
                    .vehicleId(session.getVehicleId())
                    .plateNumber(session.getPlateNumber())
                    .tripId(session.getTripId())
                    .tripCode("TRIP-" + session.getTripId())
                    .tripStatus(TripStatus.COMPLETED)
                    .routeId(session.getRouteId())
                    .routeName(session.getRouteName())
                    .latitude(newPos.getLatitude())
                    .longitude(newPos.getLongitude())
                    .speed(0.0)
                    .heading(round(heading, 1))
                    .status(VehicleStatus.IDLE)
                    .currentStopIndex(nextIndex)
                    .targetStationId(null)
                    .targetStationName("Đã hoàn thành")
                    .distanceToTargetMeters(0.0)
                    .etaSecondsToTarget(0L)
                    .etaSecondsToCompletion(0L)
                    .estimatedCompletionTime(completionTime)
                    .stationsEta(stationEtas)
                    .inIncidentZone(inIncidentZone)
                    .currentIncidentNotice(incidentNotice)
                    .timestamp(LocalDateTime.now(clock))
                    .build();

            messagingTemplate.convertAndSend("/topic/telemetry", telemetry);
            messagingTemplate.convertAndSend("/topic/vehicle/" + session.getVehicleId(), telemetry);
            log.info("Chuyến đi {} đã hoàn thành mô phỏng", session.getTripId());
        } else {
            // Cập nhật vị trí xe
            try {
                vehicleRepository.findById(session.getVehicleId()).ifPresent(v -> {
                    v.setCurrentLatitude(newPos.getLatitude());
                    v.setCurrentLongitude(newPos.getLongitude());
                    v.setCurrentSpeed(round(effectiveSpeedKmh, 1));
                    v.setCurrentHeading(round(heading, 1));
                    v.setLastUpdatedAt(LocalDateTime.now(clock));
                    vehicleRepository.save(v);
                });
            } catch (Exception ignored) {}

            StationEtaDto nextTarget = stationEtas.stream()
                    .filter(eta -> eta.getStatus() == CheckInStatus.PENDING)
                    .findFirst()
                    .orElse(null);

            Long etaSecondsToCompletion = (finalEta != null) ? finalEta.getEtaSeconds() : 0L;
            LocalDateTime estimatedCompletionTime = (finalEta != null) ? finalEta.getEstimatedArrivalTime() : LocalDateTime.now(clock);

            VehicleTelemetryDto telemetry = VehicleTelemetryDto.builder()
                    .vehicleId(session.getVehicleId())
                    .plateNumber(session.getPlateNumber())
                    .tripId(session.getTripId())
                    .tripCode("TRIP-" + session.getTripId())
                    .tripStatus(TripStatus.RUNNING)
                    .routeId(session.getRouteId())
                    .routeName(session.getRouteName())
                    .latitude(newPos.getLatitude())
                    .longitude(newPos.getLongitude())
                    .speed(round(effectiveSpeedKmh, 1))
                    .heading(round(heading, 1))
                    .status(VehicleStatus.IN_TRANSIT)
                    .currentStopIndex(nextIndex)
                    .targetStationId(nextTarget != null ? nextTarget.getStationId() : null)
                    .targetStationName(nextTarget != null ? nextTarget.getStationName() : "Đã hoàn thành")
                    .distanceToTargetMeters(nextTarget != null ? nextTarget.getDistanceRemainingMeters() : 0.0)
                    .etaSecondsToTarget(nextTarget != null ? nextTarget.getEtaSeconds() : 0L)
                    .etaSecondsToCompletion(etaSecondsToCompletion)
                    .estimatedCompletionTime(estimatedCompletionTime)
                    .stationsEta(stationEtas)
                    .inIncidentZone(inIncidentZone)
                    .currentIncidentNotice(incidentNotice)
                    .timestamp(LocalDateTime.now(clock))
                    .build();

            messagingTemplate.convertAndSend("/topic/telemetry", telemetry);
            messagingTemplate.convertAndSend("/topic/vehicle/" + session.getVehicleId(), telemetry);
        }
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
