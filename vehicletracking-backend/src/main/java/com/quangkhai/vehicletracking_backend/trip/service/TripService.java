package com.quangkhai.vehicletracking_backend.trip.service;

import com.quangkhai.vehicletracking_backend.trip.dto.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final RouteRepository routes;
    private final Clock operationsClock;

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
        var departure = input.scheduledDepartureAt().truncatedTo(ChronoUnit.MICROS);
        if (departure.isBefore(Instant.parse("2000-01-01T00:00:00Z")) ||
                !departure.isBefore(Instant.parse("2101-01-01T00:00:00Z")))
            throw new ResponseStatusException(BAD_REQUEST, "Giờ xuất phát phải nằm trong năm 2000–2100.");
        VehicleEntity vehicle = lockVehicle(input.vehicleId());
        requireActive(vehicle);
        var route = routes.findById(input.routeId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tuyến đường."));
        if (!route.isActive()) throw new ResponseStatusException(CONFLICT, "Tuyến đã ngừng sử dụng.");
        if (route.getStops().size() < 2) throw new ResponseStatusException(CONFLICT, "Tuyến chưa có đủ điểm dừng.");
        if (route.getStops().stream().anyMatch(stop -> !stop.getStation().isActive()))
            throw new ResponseStatusException(CONFLICT, "Tuyến có trạm đã ngừng sử dụng. Hãy tạo tuyến khác từ các trạm đang hoạt động.");
        var detail = RouteDetailResponse.from(route);
        TripEntity trip = new TripEntity(vehicle, route, departure);
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
        trip.reschedule(departure);
        return TripDetailResponse.from(trip);
    }
    @Transactional
    public void delete(long id) {
        TripEntity trip = trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() != TripStatus.SCHEDULED)
            throw new ResponseStatusException(CONFLICT, "Chỉ có thể xóa chuyến chưa khởi hành.");
        try { trips.delete(trip); trips.flush(); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Chuyến đã có dữ liệu vận hành và không thể xóa.", ex);
        }
    }
    @Transactional
    public TripDetailResponse start(long id) { return transition(id, TripStatus.IN_PROGRESS); }
    @Transactional
    public TripDetailResponse complete(long id) { return transition(id, TripStatus.COMPLETED); }
    @Transactional
    public TripDetailResponse cancel(long id) { return transition(id, TripStatus.CANCELLED); }

    private TripDetailResponse transition(long id, TripStatus target) {
        TripEntity trip = trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến đi."));
        if (trip.getStatus() == target) return TripDetailResponse.from(trip);
        // Same vehicle lock as create/deactivate. Different trips for this vehicle serialize here.
        VehicleEntity vehicle = lockVehicle(trip.getVehicle().getId());
        Instant now = clockNow();
        switch (target) {
            case IN_PROGRESS -> {
                if (trip.getStatus() != TripStatus.SCHEDULED) throw invalidTransition();
                requireActive(vehicle);
                if (trips.existsByVehicleIdAndStatusIn(vehicle.getId(), List.of(TripStatus.IN_PROGRESS)))
                    throw new ResponseStatusException(CONFLICT, "Xe đang chạy một chuyến khác.");
                trip.start(now);
            }
            case COMPLETED -> {
                if (trip.getStatus() != TripStatus.IN_PROGRESS) throw invalidTransition();
                trip.complete(now);
            }
            case CANCELLED -> {
                if (trip.getStatus() != TripStatus.SCHEDULED && trip.getStatus() != TripStatus.IN_PROGRESS) throw invalidTransition();
                trip.cancel(now);
            }
            default -> throw invalidTransition();
        }
        try { trips.flush(); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Trạng thái chuyến đã thay đổi hoặc xe đang chạy chuyến khác. Hãy tải lại.", ex);
        }
        return TripDetailResponse.from(trip);
    }
    private VehicleEntity lockVehicle(long id) {
        return vehicles.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy xe."));
    }
    private void requireActive(VehicleEntity vehicle) {
        if (!vehicle.isActive()) throw new ResponseStatusException(CONFLICT, "Xe đã ngừng sử dụng.");
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
}
