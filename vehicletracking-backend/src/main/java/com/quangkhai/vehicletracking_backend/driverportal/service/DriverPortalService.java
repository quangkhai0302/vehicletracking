package com.quangkhai.vehicletracking_backend.driverportal.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.checkin.dto.StopVisitResponse;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.driverportal.dto.DriverScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.schedule.service.ScheduleOccurrenceResolver;
import com.quangkhai.vehicletracking_backend.trip.dto.TripDetailResponse;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverPortalService {
    private final TripRepository trips;
    private final TripScheduleRepository schedules;
    private final TripStopVisitRepository visits;
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

    @Transactional
    public StopVisitResponse confirmBoardingCount(UserAccountPrincipal principal, long tripId, int sequence, int count) {
        long driverId = driverId(principal);
        var trip = trips.findLockedByIdAndDriverId(tripId, driverId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến được phân công."));
        if (trip.getStatus() != TripStatus.IN_PROGRESS)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Chuyến chưa khởi hành.");
        var stop = trip.getStops().stream().filter(item -> item.getSequenceNumber() == sequence).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy trạm của chuyến."));
        var finalSequence = trip.getStops().stream().mapToInt(item -> item.getSequenceNumber()).max().orElse(-1);
        if (sequence == finalSequence)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Điểm đến cuối không nhận người lên xe.");
        var visit = visits.findByTripIdAndAttemptNumberAndStopSequence(tripId, trip.getAttemptNumber(), sequence)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Xe chưa check-in tại trạm này."));
        if (visit.getEmployeeBoardingCount() != null) {
            if (visit.getEmployeeBoardingCount() == count) return StopVisitResponse.from(visit);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Số người tại trạm này đã được xác nhận.");
        }
        var currentVisits = visits.findAllByTripIdAndAttemptNumberOrderByStopSequenceAsc(tripId, trip.getAttemptNumber());
        long cumulative = count;
        for (var earlierStop : trip.getStops().stream().sorted(java.util.Comparator.comparingInt(item -> item.getSequenceNumber())).toList()) {
            if (earlierStop.getSequenceNumber() >= sequence) break;
            if (earlierStop.getSequenceNumber() == finalSequence) continue;
            var earlier = currentVisits.stream()
                    .filter(item -> item.getStopSequence().equals(earlierStop.getSequenceNumber()))
                    .findFirst().orElse(null);
            if (earlier == null || earlier.getEmployeeBoardingCount() == null)
                throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Hãy xác nhận số người lên ở trạm trước trước khi tiếp tục.");
            cumulative += earlier.getEmployeeBoardingCount();
        }
        Integer capacity = trip.getVehicle().getSeatCapacity();
        if (capacity != null && cumulative > capacity)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Số người trên xe vượt quá sức chứa.");
        visit.confirmEmployeeBoarding(count);
        return StopVisitResponse.from(visit);
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
