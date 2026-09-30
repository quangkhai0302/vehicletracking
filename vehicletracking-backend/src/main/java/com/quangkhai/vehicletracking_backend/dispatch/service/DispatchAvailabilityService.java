package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchOfferStatus;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchState;
import com.quangkhai.vehicletracking_backend.dispatch.entity.TripDispatchEntity;
import com.quangkhai.vehicletracking_backend.dispatch.repository.DriverUnavailabilityRepository;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchOfferRepository;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchRepository;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class DispatchAvailabilityService {
    private static final long BUFFER_SECONDS = 15 * 60;
    private final TripRepository trips;
    private final TripDispatchRepository dispatches;
    private final TripDispatchOfferRepository offers;
    private final DriverUnavailabilityRepository busy;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final UserAccountRepository accounts;
    private final Clock operationsClock;

    public boolean driverAvailable(long driverId, TripEntity target, long durationSeconds, boolean allowOwnOffer) {
        if (drivers.findById(driverId).filter(item -> item.isActive()).isEmpty()
                || !accounts.existsActiveDriverAccount(driverId)) return false;
        Instant start = target.getScheduledDepartureAt().minusSeconds(BUFFER_SECONDS);
        Instant end = target.getScheduledDepartureAt().plusSeconds(durationSeconds + BUFFER_SECONDS);
        if (busy.existsByDriverIdAndStartsAtLessThanAndEndsAtGreaterThan(driverId, end, start)) return false;
        if (!allowOwnOffer && offers.existsByCandidateDriverIdAndStatus(driverId, DispatchOfferStatus.PENDING)) return false;
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
            if (other.getStatus() != TripStatus.SCHEDULED || other.getSchedule() == null) continue;
            if (overlaps(other, start, end)) return false;
        }
        return true;
    }

    /** Guard for legacy writers without changing their unrelated scheduling rules. */
    public boolean driverReservedForAuto(long driverId, Instant departure, long durationSeconds, Long exceptTripId) {
        Instant start = departure.minusSeconds(BUFFER_SECONDS);
        Instant end = departure.plusSeconds(Math.max(1, durationSeconds) + BUFFER_SECONDS);
        if (busy.existsByDriverIdAndStartsAtLessThanAndEndsAtGreaterThan(driverId, end, start)) return true;
        for (var offer : offers.findAllByCandidateDriverIdAndStatusOrderByExpiresAtAsc(driverId, DispatchOfferStatus.PENDING)) {
            TripDispatchEntity dispatch = offer.getDispatch();
            if (exceptTripId != null && exceptTripId.equals(dispatch.getTripId())) continue;
            if (offer.getExpiresAt().isAfter(operationsClock.instant()) && activeReservation(dispatch)
                    && overlaps(dispatch.getTrip(), start, end)) return true;
        }
        for (TripEntity other : trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(driverId)) {
            if (exceptTripId != null && exceptTripId.equals(other.getId())) continue;
            if (other.getStatus() != TripStatus.SCHEDULED) continue;
            if (dispatches.findById(other.getId()).filter(this::activeReservation).isPresent()
                    && overlaps(other, start, end)) return true;
        }
        return false;
    }

    public boolean vehicleReservedForAuto(long vehicleId, Instant departure, long durationSeconds, Long exceptTripId) {
        Instant start = departure.minusSeconds(BUFFER_SECONDS);
        Instant end = departure.plusSeconds(Math.max(1, durationSeconds) + BUFFER_SECONDS);
        for (TripEntity other : trips.findAllByVehicleIdOrderByScheduledDepartureAtDescIdDesc(vehicleId)) {
            if (exceptTripId != null && exceptTripId.equals(other.getId())) continue;
            if (other.getStatus() != TripStatus.SCHEDULED) continue;
            if (dispatches.findById(other.getId()).filter(this::activeReservation).isPresent()
                    && overlaps(other, start, end)) return true;
        }
        return false;
    }

    private boolean activeReservation(TripDispatchEntity dispatch) {
        TripEntity trip = dispatch.getTrip();
        return dispatch.getStartMode() == DispatchStartMode.AUTO_IF_READY
                && dispatch.getState() != DispatchState.CLOSED && dispatch.getState() != DispatchState.STARTED
                && trip.getStatus() == TripStatus.SCHEDULED && trip.getSchedule() != null
                && trip.getSchedule().isEnabled()
                && dispatch.getScheduleEpoch() == trip.getSchedule().getDispatchEpoch();
    }

    private boolean overlaps(TripEntity other, Instant start, Instant end) {
        long duration = dispatches.findById(other.getId()).map(item -> item.getBaselineDurationSeconds())
                .orElseGet(() -> Math.max(1, other.getStops().stream()
                        .mapToLong(stop -> stop.getDepartureOffsetSeconds()).max().orElse(1)));
        Instant otherStart = other.getScheduledDepartureAt().minusSeconds(BUFFER_SECONDS);
        Instant otherEnd = other.getScheduledDepartureAt().plusSeconds(duration + BUFFER_SECONDS);
        return otherStart.isBefore(end) && start.isBefore(otherEnd);
    }
}
