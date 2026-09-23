package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.event.TripStartedEvent;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.TripLifecycleProperties;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import java.time.DateTimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class TripService {
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final RouteRepository routes;
    private final TripScheduleRepository schedules;
    private final TripStopVisitRepository visits;
    private final TripLifecycleProperties lifecycle;
    private final Clock operationsClock;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> findAll(Long vehicleId) {
        var result = vehicleId == null ? trips.findAllByOrderByScheduledDepartureAtDescIdDesc()
                : trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(vehicleId);
        return result.stream().map(TripSummaryResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public TripDetailResponse findById(long id) {
        return TripDetailResponse.from(trips.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi.")));
    }
    @Transactional
    public TripDetailResponse create(TripCreateRequest input) {
        return createInternal(input.vehicleId(), input.routeId(), input.driverId(), input.scheduledDepartureAt(), null, null);
    }
    /** Creates a trip occurrence while preserving the same resource and route checks as manual creation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TripDetailResponse createFromSchedule(TripScheduleEntity schedule, Instant occurrenceAt) {
        TripScheduleEntity managedSchedule = schedules.getReferenceById(schedule.getId());
        if (!managedSchedule.isEnabled())
            throw new ResponseStatusException(CONFLICT, "Lịch chạy đã được tạm dừng.");
        if (managedSchedule.getVersion() != schedule.getVersion())
            throw new ResponseStatusException(CONFLICT, "Lịch chạy đã thay đổi trong lúc tạo chuyến.");
        return createInternal(managedSchedule.getVehicle().getId(), managedSchedule.getRoute().getId(), managedSchedule.getDriver().getId(),
                occurrenceAt, managedSchedule, occurrenceAt);
    }
    private TripDetailResponse createInternal(long vehicleId, long routeId, Long driverId, Instant scheduledDepartureAt,
            TripScheduleEntity schedule, Instant occurrenceAt) {
        var departure = scheduledDepartureAt.truncatedTo(ChronoUnit.MICROS);
        validateDeparture(departure);
        VehicleEntity vehicle = lockVehicle(vehicleId);
        requireActive(vehicle);
        DriverEntity driver = resolveDriver(driverId);
        var routeLookup = schedule == null ? routes.findById(routeId) : routes.findLockedById(routeId);
        var route = routeLookup.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tuyến đường."));
        if (!route.isActive()) throw new ResponseStatusException(CONFLICT, "Tuyến đã ngừng sử dụng.");
        if (route.getStops().size() < 2) throw new ResponseStatusException(CONFLICT, "Tuyến chưa có đủ điểm dừng.");
        if (route.getStops().stream().anyMatch(stop -> !stop.getStation().isActive()))
            throw new ResponseStatusException(CONFLICT, "Tuyến có trạm đã ngừng sử dụng. Hãy tạo tuyến khác từ các trạm đang hoạt động.");
        ensureNoResourceConflict(vehicleId, driver == null ? null : driver.getId(), departure,
                route.getEstimatedTripDurationSeconds(), null);
        var detail = RouteDetailResponse.from(route);
        TripEntity trip = new TripEntity(vehicle, route, departure, driver, schedule, occurrenceAt == null ? null : occurrenceAt.truncatedTo(ChronoUnit.MICROS));
        try {
            departure.plusSeconds(route.getEstimatedTripDurationSeconds());
            for (int i = 0; i < detail.stops().size(); i++) {
                trip.addStop(new TripStopEntity(detail.stops().get(i),
                        route.getStops().get(i).getStation().getCheckinRadiusMeters(), departure));
            }
        } catch (DateTimeException | ArithmeticException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Thời gian lịch trình vượt phạm vi hỗ trợ.");
        }
        return TripDetailResponse.from(trips.saveAndFlush(trip));
    }
    @Transactional
    public TripDetailResponse update(long id, TripUpdateRequest input) {
        TripEntity trip = trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() != TripStatus.SCHEDULED)
            throw new ResponseStatusException(CONFLICT, "Chỉ có thể sửa lịch chuyến chưa khởi hành.");
        Instant departure = input.scheduledDepartureAt().truncatedTo(ChronoUnit.MICROS);
        validateDeparture(departure);
        VehicleEntity vehicle = lockVehicle(trip.getVehicle().getId());
        requireActive(vehicle);
        DriverEntity driver = trip.getDriver() == null ? null : lockDriver(trip.getDriver().getId());
        if (driver != null) requireActive(driver);
        ensureNoResourceConflict(vehicle.getId(), driver == null ? null : driver.getId(), departure,
                trip.getRoute().getEstimatedTripDurationSeconds(), trip.getId());
        trip.reschedule(departure);
        return TripDetailResponse.from(trip);
    }
    @Transactional
    public void delete(long id) {
        TripEntity trip = trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() != TripStatus.SCHEDULED)
            throw new ResponseStatusException(CONFLICT, "Chỉ có thể xóa chuyến chưa khởi hành.");
        if (trip.getSchedule() != null)
            throw new ResponseStatusException(CONFLICT, "Chuyến được sinh từ lịch chạy không thể xóa; hãy hủy chuyến hoặc tạm dừng lịch.");
        try { trips.delete(trip); trips.flush(); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Chuyến đã có dữ liệu vận hành và không thể xóa.", ex);
        }
    }
    @Transactional
    public TripDetailResponse assignDriver(long id, long driverId) {
        TripEntity trip = findScheduledLocked(id);
        VehicleEntity vehicle = lockVehicle(trip.getVehicle().getId());
        requireActive(vehicle);
        DriverEntity driver = lockDriver(driverId);
        requireActive(driver);
        ensureNoResourceConflict(vehicle.getId(), driver.getId(), trip.getScheduledDepartureAt(),
                trip.getRoute().getEstimatedTripDurationSeconds(), trip.getId());
        trip.assignDriver(driver);
        return flushAssignment(trip);
    }
    @Transactional
    public void unassignDriver(long id) {
        TripEntity trip = findScheduledLocked(id);
        trip.assignDriver(null);
        trips.flush();
    }
    @Transactional
    public TripDetailResponse start(long id) { return transition(id, TripStatus.IN_PROGRESS); }
    @Transactional
    public TripDetailResponse complete(long id) { return transition(id, TripStatus.COMPLETED); }
    @Transactional
    public TripDetailResponse cancel(long id) { return cancel(id, "Hủy chuyến theo yêu cầu điều phối."); }
    @Transactional
    public TripDetailResponse cancel(long id, String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500)
            throw new ResponseStatusException(BAD_REQUEST, "Lý do hủy chuyến phải dài từ 3 đến 500 ký tự.");
        return transition(id, TripStatus.CANCELLED, normalized);
    }

    private TripDetailResponse transition(long id, TripStatus target) {
        return transition(id, target, null);
    }

    private TripDetailResponse transition(long id, TripStatus target, String cancellationReason) {
        TripEntity trip = trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() == target) return TripDetailResponse.from(trip);
        // Same vehicle lock as create/deactivate. Different trips for this vehicle serialize here.
        VehicleEntity vehicle = lockVehicle(trip.getVehicle().getId());
        Instant now = clockNow();
        switch (target) {
            case IN_PROGRESS -> {
                if (trip.getStatus() != TripStatus.SCHEDULED) throw invalidTransition();
                requireActive(vehicle);
                if (trip.getDriver() == null)
                    throw new ResponseStatusException(CONFLICT, "Chuyến phải được gán tài xế trước khi khởi hành.");
                DriverEntity driver = lockDriver(trip.getDriver().getId());
                requireActive(driver);
                if (trips.existsByDriverIdAndStatusAndIdNot(driver.getId(), TripStatus.IN_PROGRESS, trip.getId()))
                    throw new ResponseStatusException(CONFLICT, "Tài xế đang chạy một chuyến khác.");
                if (trips.existsByVehicleIdAndStatusIn(vehicle.getId(), List.of(TripStatus.IN_PROGRESS)))
                    throw new ResponseStatusException(CONFLICT, "Xe đang chạy một chuyến khác.");
                ensureNoResourceConflict(vehicle.getId(), driver.getId(), trip.getScheduledDepartureAt(),
                        trip.getRoute().getEstimatedTripDurationSeconds(), trip.getId());
                ensureStartWindow(trip.getScheduledDepartureAt(), now);
                trip.start(now);
            }
            case COMPLETED -> {
                if (trip.getStatus() != TripStatus.IN_PROGRESS) throw invalidTransition();
                int finalStopSequence = trip.getStops().stream().mapToInt(stop -> stop.getSequenceNumber()).max().orElse(0);
                if (finalStopSequence == 0 || !visits.existsByTripIdAndStopSequence(trip.getId(), finalStopSequence))
                    throw new ResponseStatusException(CONFLICT, "Chưa ghi nhận xe đến trạm cuối của chuyến.");
                trip.complete(now);
            }
            case CANCELLED -> {
                if (trip.getStatus() != TripStatus.SCHEDULED && trip.getStatus() != TripStatus.IN_PROGRESS) throw invalidTransition();
                trip.cancel(now, cancellationReason == null ? "Hủy chuyến theo yêu cầu điều phối." : cancellationReason);
            }
            default -> throw invalidTransition();
        }
        try { trips.flush(); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Trạng thái chuyến đã thay đổi hoặc xe/tài xế đang chạy chuyến khác. Hãy tải lại.", ex);
        }
        if (target == TripStatus.IN_PROGRESS) events.publishEvent(new TripStartedEvent(trip.getId()));
        return TripDetailResponse.from(trip);
    }
    private VehicleEntity lockVehicle(long id) {
        return vehicles.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy xe."));
    }
    private DriverEntity resolveDriver(Long driverId) {
        if (driverId == null) return null;
        DriverEntity driver = lockDriver(driverId);
        requireActive(driver);
        return driver;
    }
    private DriverEntity lockDriver(long id) {
        return drivers.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
    }
    private void requireActive(VehicleEntity vehicle) {
        if (!vehicle.isActive()) throw new ResponseStatusException(CONFLICT, "Xe đã ngừng sử dụng.");
    }
    private void requireActive(DriverEntity driver) {
        if (!driver.isActive()) throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng sử dụng.");
    }
    private TripEntity findScheduledLocked(long id) {
        TripEntity trip = trips.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() != TripStatus.SCHEDULED)
            throw new ResponseStatusException(CONFLICT, "Chỉ có thể đổi tài xế của chuyến chưa khởi hành.");
        return trip;
    }
    private TripDetailResponse flushAssignment(TripEntity trip) {
        try {
            trips.flush();
            return TripDetailResponse.from(trip);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Không thể gán tài xế. Hãy tải lại.", ex);
        }
    }
    private ResponseStatusException invalidTransition() {
        return new ResponseStatusException(CONFLICT, "Không thể thực hiện thao tác với trạng thái chuyến hiện tại. Hãy tải lại.");
    }
    private Instant clockNow() {
        return (operationsClock == null ? Instant.now() : operationsClock.instant()).truncatedTo(ChronoUnit.MICROS);
    }
    private void validateDeparture(Instant departure) {
        if (departure.isBefore(Instant.parse("2000-01-01T00:00:00Z")) || !departure.isBefore(Instant.parse("2101-01-01T00:00:00Z")))
            throw new ResponseStatusException(BAD_REQUEST, "Giờ xuất phát phải nằm trong năm 2000–2100.");
    }

    private void ensureStartWindow(Instant scheduledDeparture, Instant now) {
        if (lifecycle == null) return;
        Instant earliest = scheduledDeparture.minusSeconds(lifecycle.getEarlyStartWindowSeconds());
        Instant latest = scheduledDeparture.plusSeconds(lifecycle.getLateStartWindowSeconds());
        if (now.isBefore(earliest) || now.isAfter(latest))
            throw new ResponseStatusException(CONFLICT, "Ngoài khung giờ khởi hành cho phép của chuyến.");
    }

    private void ensureNoResourceConflict(long vehicleId, Long driverId, Instant departure,
                                           long durationSeconds, Long exceptTripId) {
        Instant end = departure.plusSeconds(Math.max(0, durationSeconds));
        List<TripStatus> activeStatuses = List.of(TripStatus.SCHEDULED, TripStatus.IN_PROGRESS);
        for (TripEntity other : trips.findAllByVehicleIdAndStatusIn(vehicleId, activeStatuses)) {
            if (exceptTripId != null && exceptTripId.equals(other.getId())) continue;
            if (overlaps(other, departure, end))
                throw new ResponseStatusException(CONFLICT, "Xe đã có chuyến bị chồng thời gian dự kiến.");
        }
        if (driverId == null) return;
        for (TripEntity other : trips.findAllByDriverIdAndStatusIn(driverId, activeStatuses)) {
            if (exceptTripId != null && exceptTripId.equals(other.getId())) continue;
            if (overlaps(other, departure, end))
                throw new ResponseStatusException(CONFLICT, "Tài xế đã có chuyến bị chồng thời gian dự kiến.");
        }
    }

    private boolean overlaps(TripEntity other, Instant departure, Instant end) {
        Instant otherStart = other.getScheduledDepartureAt();
        Instant otherEnd = otherStart.plusSeconds(Math.max(0, other.getRoute().getEstimatedTripDurationSeconds()));
        return otherStart.isBefore(end) && departure.isBefore(otherEnd);
    }
}
