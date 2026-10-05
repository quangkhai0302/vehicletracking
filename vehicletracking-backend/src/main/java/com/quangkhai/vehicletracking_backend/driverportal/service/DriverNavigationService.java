package com.quangkhai.vehicletracking_backend.driverportal.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driverportal.dto.*;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.*;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse.RouteSectionResponse;
import com.quangkhai.vehicletracking_backend.route.provider.*;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.checkin.service.CheckInQueryService;
import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import com.quangkhai.vehicletracking_backend.simulation.motion.FlexiblePolyline;
import com.quangkhai.vehicletracking_backend.simulation.entity.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryResponse;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class DriverNavigationService {
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final SimulationRepository runs;
    private final VehiclePositionRepository positions;
    private final SimulationService simulation;
    private final TripRouteGeometryService geometry;
    private final RoutingProvider routing;
    private final TripRouteRevisionRepository revisions;
    private final TripNotificationRepository notifications;
    private final Clock operationsClock;
    private final CheckInQueryService checkIns;
    private final StationRepository stations;

    private record Preview(long driverId, long tripId, int attempt, Long version, int nextStop,
                           Instant expiresAt, List<DriverRouteOptionsResponse.Option> options) {}
    // Ephemeral provider responses are bounded and expire; durable changes remain in JPA revisions.
    private final Map<UUID, Preview> previews = new LinkedHashMap<>();

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DriverNavigationResponse navigation(UserAccountPrincipal principal, long tripId) {
        return describe(owned(principal, tripId, false));
    }

    @Transactional
    public DriverNavigationResponse start(UserAccountPrincipal principal, long tripId) {
        var trip = owned(principal, tripId, true);
        var run = runs.findByTripId(tripId).orElse(null);
        if (trip.getStatus() == TripStatus.IN_PROGRESS && run != null && run.getStatus() == SimulationStatus.RUNNING)
            return describe(trip);
        if (trip.getStatus() != TripStatus.SCHEDULED)
            throw conflict("Chuyến đã bắt đầu hoặc kết thúc. Không thể tự tiếp tục phiên mô phỏng đã tạm dừng.");
        simulation.play(tripId);
        return describe(trip);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DriverRouteOptionsResponse options(UserAccountPrincipal principal, long tripId) {
        var trip = owned(principal, tripId, false);
        var run = requireRunning(trip);
        var plan = geometry.resolve(trip);
        var frame = plan.motion().at(run.getElapsedSeconds());
        if (frame.dwelling() || frame.finished()) throw conflict("Hãy chọn đường khi xe đang di chuyển giữa hai trạm.");
        var remaining = remaining(trip, frame.nextStopSequence());
        var waypoints = new ArrayList<RoutingWaypoint>();
        waypoints.add(new RoutingWaypoint(null, "Vị trí xe hiện tại", BigDecimal.valueOf(frame.latitude()),
                BigDecimal.valueOf(frame.longitude()), 1, 0));
        for (int i = 0; i < remaining.size(); i++) {
            var stop = remaining.get(i);
            waypoints.add(new RoutingWaypoint(stop.getStationId(), stop.getStationName(), stop.getLatitude(),
                    stop.getLongitude(), i + 2, stop.getDwellDurationSeconds()));
        }
        var candidates = new ArrayList<DriverRouteOptionsResponse.Option>();
        var fingerprints = new HashSet<String>();
        List<CalculatedRoute> calculated;
        try { calculated = routing.calculateAlternatives(waypoints); }
        catch (RouteOperationException ex) {
            throw new ResponseStatusException(ex.getStatus(), "Không thể lấy đường từ HERE. Hãy thử lại sau.", ex);
        }
        for (var candidate : calculated) {
            if (candidates.size() == 3) break;
            var sections = candidate.sections().stream().map(s -> {
                int destination = s.destinationStopSequence() - 2;
                if (destination < 0 || destination >= remaining.size()) throw conflict("Nhà cung cấp trả đường thiếu trạm.");
                return new RouteSectionResponse(s.sectionSequence(), remaining.get(destination).getSequenceNumber(),
                        s.encodedPolyline(), s.distanceMeters(), s.travelDurationSeconds(), s.baseTravelDurationSeconds(), s.instructions());
            }).toList();
            String fingerprint = sections.stream().map(RouteSectionResponse::encodedPolyline).collect(Collectors.joining("|"));
            if (!fingerprints.add(fingerprint)) continue;
            // Reject omitted stops, disconnected or unusable provider geometry before presenting it.
            try {
                validateStops(trip, sections);
                geometry.validateReplacement(trip, DriverRouteRebaser.rebase(sections, frame.latitude(), frame.longitude()), run.getElapsedSeconds());
            }
            catch (IllegalArgumentException ex) { continue; }
            long distance = sections.stream().mapToLong(RouteSectionResponse::distanceMeters).sum();
            long duration = sections.stream().mapToLong(RouteSectionResponse::travelDurationSeconds).sum()
                    + remaining.stream().mapToLong(TripStopEntity::getDwellDurationSeconds).sum();
            int index = candidates.size();
            candidates.add(new DriverRouteOptionsResponse.Option(index, index == 0 ? "Đường đề xuất" : "Đường thay thế " + index,
                    distance, duration, sections));
        }
        UUID token = UUID.randomUUID();
        Instant expires = operationsClock.instant().plusSeconds(120);
        synchronized (previews) {
            previews.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(operationsClock.instant()));
            // A new preview invalidates older uncommitted choices on this trip/account.
            previews.entrySet().removeIf(e -> e.getValue().tripId() == tripId && e.getValue().driverId() == principal.driverId());
            if (previews.size() >= 200) previews.remove(previews.keySet().iterator().next());
            previews.put(token, new Preview(principal.driverId(), tripId, trip.getAttemptNumber(), plan.revisionId(),
                    frame.nextStopSequence(), expires, List.copyOf(candidates)));
        }
        return new DriverRouteOptionsResponse(token, expires, plan.revisionId(), candidates);
    }

    @Transactional
    public DriverNavigationResponse apply(UserAccountPrincipal principal, long tripId, UUID token, int optionIndex) {
        var trip = owned(principal, tripId, true);
        String key = "DRIVER_ROUTE_CHANGED:" + token;
        var applied = notifications.findByDedupeKey(key).orElse(null);
        if (applied != null) {
            if (!applied.getTrip().getId().equals(tripId)) throw notFound();
            return describe(trip); // Durable idempotency, including after a server restart/lost response.
        }
        Preview preview;
        synchronized (previews) { preview = previews.get(token); }
        if (preview == null) throw conflict("Phương án đã hết hạn. Hãy lấy đường thay thế mới.");
        if (preview.tripId() != tripId || preview.driverId() != principal.driverId()) throw notFound();
        if (optionIndex < 0 || optionIndex >= preview.options().size())
            throw new ResponseStatusException(BAD_REQUEST, "Phương án đường không hợp lệ.");
        if (!preview.expiresAt().isAfter(operationsClock.instant()) || preview.attempt() != trip.getAttemptNumber())
            throw conflict("Phương án đã hết hạn. Hãy lấy đường thay thế mới.");
        requireRunning(trip);
        simulation.tick(tripId); // Advance under the same trip/vehicle locks before choosing an exact anchor.
        var run = requireRunning(trip);
        var plan = geometry.resolve(trip);
        var frame = plan.motion().at(run.getElapsedSeconds());
        if (!Objects.equals(preview.version(), plan.revisionId()) || frame.nextStopSequence() != preview.nextStop()
                || frame.dwelling() || frame.finished())
            throw conflict("Vị trí hoặc lộ trình đã thay đổi. Hãy lấy đường thay thế mới.");
        List<RouteSectionResponse> sections;
        try {
            sections = DriverRouteRebaser.rebase(preview.options().get(optionIndex).sections(), frame.latitude(), frame.longitude());
            geometry.validateReplacement(trip, sections, run.getElapsedSeconds());
        } catch (IllegalArgumentException ex) {
            throw conflict("Xe đã đi khỏi điểm chọn đường. Hãy lấy đường thay thế mới.");
        }
        Instant now = operationsClock.instant();
        var remaining = remaining(trip, frame.nextStopSequence());
        long duration = sections.stream().mapToLong(RouteSectionResponse::travelDurationSeconds).sum()
                + remaining.stream().mapToLong(TripStopEntity::getDwellDurationSeconds).sum();
        String reason = "Tài xế " + trip.getDriver().getFullName() + " đã chọn đường đi mới.";
        if (reason.length() > 255) reason = reason.substring(0, 255);
        var revision = new TripRouteRevisionEntity(trip, trip.getRoute(), revisions.countByTripId(tripId) + 1,
                RerouteReasonCode.DRIVER_CHOICE, reason, null, NotificationSeverity.MAJOR,
                Math.round(plan.motion().duration() - run.getElapsedSeconds()), duration, now);
        revisions.findTopByTripIdAndStatusOrderByRevisionNumberDesc(tripId, RouteRevisionStatus.ACTIVE)
                .ifPresent(active -> { active.supersede(now); revisions.saveAndFlush(active); });
        for (var section : sections) revision.addSection(new TripRouteRevisionSectionEntity(section.sectionSequence(),
                section.destinationStopSequence(), section.encodedPolyline(), section.distanceMeters(),
                section.travelDurationSeconds(), section.baseTravelDurationSeconds(), section.instructions()));
        Instant cursor = now;
        for (int i = 0; i < remaining.size(); i++) {
            var stop = remaining.get(i);
            cursor = cursor.plusSeconds(sections.stream().filter(s -> s.destinationStopSequence() == stop.getSequenceNumber())
                    .mapToLong(RouteSectionResponse::travelDurationSeconds).sum());
            Instant departure = cursor.plusSeconds(stop.getDwellDurationSeconds());
            revision.addStop(new TripRouteRevisionStopEntity(stop.getSequenceNumber(), i + 1, stop.getStationId(),
                    stop.getStationName(), stop.getLatitude(), stop.getLongitude(), stop.getDwellDurationSeconds(),
                    stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt(), cursor, departure));
            cursor = departure;
        }
        revisions.saveAndFlush(revision);
        simulation.refreshRoute(tripId); // Applies revision immediately and emits the shared simulator position.
        notifications.saveAndFlush(new TripNotificationEntity(trip, revision, NotificationType.DRIVER_ROUTE_CHANGED,
                NotificationSeverity.MAJOR, "Tài xế đã đổi lộ trình chuyến #" + tripId, reason, null,
                remaining.stream().map(s -> s.getSequenceNumber().toString()).collect(Collectors.joining(",")),
                revision.getBaselineRemainingSeconds(), duration, key, now));
        return describe(trip);
    }

    private DriverNavigationResponse describe(TripEntity trip) {
        var run = runs.findByTripId(trip.getId()).orElse(null);
        var plan = run == null ? null : geometry.resolve(trip);
        var position = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample())
                .filter(p -> p.getTripId().equals(trip.getId()) && p.getAttemptNumber() == trip.getAttemptNumber())
                .map(TelemetryResponse::from).orElse(null);
        return new DriverNavigationResponse(operationsClock.instant(),
                TripSummaryResponse.from(trip),
                TripDetailResponse.from(trip).stops(), plan == null ? geometry.route(trip) : plan.route(), position,
                run == null ? null : simulation.describeSnapshot(trip, run), plan == null ? null : plan.revisionId(),
                plan == null ? null : plan.motion().guidance(run.getElapsedSeconds()), checkIns.find(trip.getId()),
                stations.findAllById(trip.getStops().stream().map(TripStopEntity::getStationId).distinct().toList())
                        .stream().map(StationResponse::from).toList());
    }

    private TripEntity owned(UserAccountPrincipal principal, long tripId, boolean lock) {
        if (principal == null || principal.driverId() == null)
            throw new ResponseStatusException(FORBIDDEN, "Tài khoản chưa được gắn hồ sơ tài xế.");
        var trip = (lock ? trips.findLockedById(tripId) : trips.findByIdAndDriverId(tripId, principal.driverId()))
                .orElseThrow(DriverNavigationService::notFound);
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(principal.driverId())) throw notFound();
        if (lock) vehicles.findLockedById(trip.getVehicle().getId()).orElseThrow(DriverNavigationService::notFound);
        return trip;
    }

    private SimulationRunEntity requireRunning(TripEntity trip) {
        var run = runs.findByTripId(trip.getId()).orElseThrow(() -> conflict("Chuyến chưa chạy mô phỏng."));
        if (trip.getStatus() != TripStatus.IN_PROGRESS || run.getStatus() != SimulationStatus.RUNNING)
            throw conflict("Chỉ đổi đường khi chuyến đang mô phỏng. Phiên tạm dừng do admin điều khiển.");
        if (!trip.getDriver().isActive() || !trip.getVehicle().isActive()) throw conflict("Xe hoặc tài xế đã ngừng hoạt động.");
        var position = positions.findById(trip.getVehicle().getId()).map(p -> p.getSample()).orElse(null);
        if (position == null || !trip.getId().equals(position.getTripId()) || position.getAttemptNumber() != trip.getAttemptNumber()
                || position.getSource() != TelemetrySource.SIMULATOR) throw conflict("Chuyến không có vị trí simulator hiện tại.");
        return run;
    }

    private static List<TripStopEntity> remaining(TripEntity trip, int nextStop) {
        return trip.getStops().stream().filter(s -> s.getSequenceNumber() >= nextStop)
                .sorted(Comparator.comparingInt(TripStopEntity::getSequenceNumber)).toList();
    }
    private static void validateStops(TripEntity trip, List<RouteSectionResponse> sections) {
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            if (i + 1 < sections.size() && sections.get(i + 1).destinationStopSequence() == section.destinationStopSequence()) continue;
            var stop = trip.getStops().stream().filter(s -> s.getSequenceNumber() == section.destinationStopSequence())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown stop"));
            var endpoint = FlexiblePolyline.decode(section.encodedPolyline()).getLast();
            if (GeofenceCrossing.distance(endpoint.latitude(), endpoint.longitude(), stop.getLatitude(), stop.getLongitude()) > stop.getCheckinRadiusMeters())
                throw new IllegalArgumentException("Route does not reach mandatory stop");
        }
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(CONFLICT, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến được phân công."); }
}
