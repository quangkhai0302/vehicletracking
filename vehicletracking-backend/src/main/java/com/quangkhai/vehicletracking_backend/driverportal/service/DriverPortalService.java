package com.quangkhai.vehicletracking_backend.driverportal.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.driverportal.dto.DriverScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.service.ScheduleOccurrenceResolver;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class DriverPortalService {
    private final TripRepository trips;
    private final TripScheduleRepository schedules;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> trips(UserAccountPrincipal principal, TripStatus status,
                                           Instant from, Instant to) {
        long driverId = driverId(principal);
        var owned = trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(driverId);
        return owned.stream()
                .filter(trip -> status == null || trip.getStatus() == status)
                .filter(trip -> from == null || !trip.getScheduledDepartureAt().isBefore(from))
                .filter(trip -> to == null || trip.getScheduledDepartureAt().isBefore(to))
                .map(TripSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse trip(UserAccountPrincipal principal, long tripId) {
        long driverId = driverId(principal);
        var trip = trips.findByIdAndDriverId(tripId, driverId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến được phân công."));
        return TripDetailResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<DriverScheduleResponse> schedules(UserAccountPrincipal principal) {
        long driverId = driverId(principal);
        Instant now = operationsClock.instant();
        return schedules.findAllByDriverIdOrderByCreatedAtDescIdDesc(driverId).stream()
                .map(schedule -> DriverScheduleResponse.from(ScheduleResponse.from(schedule, nextRunAt(schedule, now))))
                .toList();
    }

    private Instant nextRunAt(com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity schedule,
                              Instant now) {
        try {
            return ScheduleOccurrenceResolver.next(schedule, now).orElse(null);
        } catch (ResponseStatusException ignored) {
            return null;
        }
    }

    private long driverId(UserAccountPrincipal principal) {
        if (principal == null || principal.driverId() == null)
            throw new ResponseStatusException(FORBIDDEN, "Tài khoản chưa được gắn với hồ sơ tài xế.");
        return principal.driverId();
    }
}
