package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DispatchAvailabilityService {
    private static final long BUFFER_SECONDS = 15 * 60;
    private final TripRepository trips;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final UserAccountRepository accounts;

    public boolean driverAvailable(long driverId, TripEntity target, long durationSeconds) {
        if (drivers.findById(driverId).filter(item -> item.isActive()).isEmpty()
                || !accounts.existsActiveDriverAccount(driverId)) return false;
        Instant start = target.getScheduledDepartureAt().minusSeconds(BUFFER_SECONDS);
        Instant end = target.getScheduledDepartureAt().plusSeconds(durationSeconds + BUFFER_SECONDS);
        for (TripEntity other : trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(driverId)) {
            if (other.getId().equals(target.getId())) continue;
            if (other.getStatus() == TripStatus.IN_PROGRESS) return false;
            if (other.getStatus() != TripStatus.SCHEDULED) continue;
            if (overlaps(other, start, end)) return false;
        }
        return true;
    }

    public boolean vehicleAvailable(long vehicleId, TripEntity target, long durationSeconds) {
        if (vehicles.findById(vehicleId).filter(item -> item.isActive()).isEmpty()) return false;
        Instant start = target.getScheduledDepartureAt().minusSeconds(BUFFER_SECONDS);
        Instant end = target.getScheduledDepartureAt().plusSeconds(durationSeconds + BUFFER_SECONDS);
        for (TripEntity other : trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(vehicleId)) {
            if (other.getId().equals(target.getId())) continue;
            if (other.getStatus() == TripStatus.IN_PROGRESS) return false;
            if (other.getStatus() != TripStatus.SCHEDULED) continue;
            if (overlaps(other, start, end)) return false;
        }
        return true;
    }

    private boolean overlaps(TripEntity other, Instant start, Instant end) {
        long duration = Math.max(1, other.getStops().stream()
                .mapToLong(stop -> stop.getDepartureOffsetSeconds()).max()
                .orElse(other.getRoute().getEstimatedTripDurationSeconds()));
        Instant otherStart = other.getScheduledDepartureAt().minusSeconds(BUFFER_SECONDS);
        Instant otherEnd = other.getScheduledDepartureAt().plusSeconds(duration + BUFFER_SECONDS);
        return otherStart.isBefore(end) && start.isBefore(otherEnd);
    }
}
