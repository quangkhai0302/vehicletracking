package com.quangkhai.vehicletracking_backend.driverportal;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverPortalService;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.checkin.entity.TripStopVisitEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStopEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySampleEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverPortalServiceTest {
    @Mock TripRepository trips;
    @Mock TripScheduleRepository schedules;
    @Mock TripStopVisitRepository visits;
    @Mock Clock clock;
    @Mock UserAccountPrincipal principal;
    @Mock TripEntity trip;
    @Mock TripStopEntity firstStop;
    @Mock TripStopEntity finalStop;
    @Mock TripStopVisitEntity visit;
    @Mock TelemetrySampleEntity sample;

    @Test
    void tripsAlwaysUsesAuthenticatedDriverScope() {
        when(principal.driverId()).thenReturn(42L);
        when(trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(42L)).thenReturn(List.of());
        DriverPortalService service = service();

        assertThat(service.trips(principal, null, null, null)).isEmpty();
        verify(trips).findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(42L);
        verifyNoMoreInteractions(trips);
    }

    @Test
    void tripDoesNotExposeAnIdOutsideAuthenticatedDriverScope() {
        when(principal.driverId()).thenReturn(42L);
        when(trips.findByIdAndDriverId(99L, 42L)).thenReturn(Optional.empty());
        DriverPortalService service = service();

        assertThatThrownBy(() -> service.trip(principal, 99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verify(trips).findByIdAndDriverId(99L, 42L);
        verify(trips, never()).findById(99L);
    }

    @Test
    void rejectsAccountWithoutLinkedDriver() {
        when(principal.driverId()).thenReturn(null);
        DriverPortalService service = service();

        assertThatThrownBy(() -> service.trips(principal, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(trips, schedules);
    }

    @Test
    void confirmsCountAtCheckedInPickupAndAllowsZeroOrPositiveValues() {
        setupTrip();
        when(visits.findByTripIdAndAttemptNumberAndStopSequence(99L, 1, 1)).thenReturn(Optional.of(visit));
        when(visits.findAllByTripIdAndAttemptNumberOrderByStopSequenceAsc(99L, 1)).thenReturn(List.of(visit));
        when(visit.getEmployeeBoardingCount()).thenReturn(null, 2);
        when(visit.getTrip()).thenReturn(trip);
        when(trip.getId()).thenReturn(99L);
        when(visit.getToSample()).thenReturn(sample);
        when(sample.getId()).thenReturn(1L);
        when(visit.getStopSequence()).thenReturn(1);
        when(visit.getAttemptNumber()).thenReturn(1);
        when(trip.getAttemptNumber()).thenReturn(1);
        when(trip.getVehicle()).thenReturn(org.mockito.Mockito.mock(com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity.class));
        when(trip.getVehicle().getSeatCapacity()).thenReturn(10);
        when(trip.getStops()).thenReturn(List.of(firstStop, finalStop));
        when(firstStop.getSequenceNumber()).thenReturn(1);
        when(finalStop.getSequenceNumber()).thenReturn(2);
        when(trip.getStatus()).thenReturn(TripStatus.IN_PROGRESS);
        when(principal.driverId()).thenReturn(42L);
        when(trips.findLockedByIdAndDriverId(99L, 42L)).thenReturn(Optional.of(trip));

        var result = service().confirmBoardingCount(principal, 99L, 1, 2);

        assertThat(result.employeeBoardingCount()).isEqualTo(2);
        verify(visit).confirmEmployeeBoarding(2);
    }

    @Test
    void rejectsBoardingThatWouldExceedCapacityAfterEarlierStops() {
        setupTrip();
        TripStopEntity middleStop = org.mockito.Mockito.mock(TripStopEntity.class);
        TripStopVisitEntity earlierVisit = org.mockito.Mockito.mock(TripStopVisitEntity.class);
        when(middleStop.getSequenceNumber()).thenReturn(2);
        when(visits.findByTripIdAndAttemptNumberAndStopSequence(99L, 1, 2)).thenReturn(Optional.of(visit));
        when(visits.findAllByTripIdAndAttemptNumberOrderByStopSequenceAsc(99L, 1)).thenReturn(List.of(earlierVisit, visit));
        when(earlierVisit.getStopSequence()).thenReturn(1);
        when(earlierVisit.getEmployeeBoardingCount()).thenReturn(8);
        when(visit.getEmployeeBoardingCount()).thenReturn((Integer) null);
        when(trip.getStops()).thenReturn(List.of(firstStop, middleStop, finalStop));
        when(firstStop.getSequenceNumber()).thenReturn(1);
        when(finalStop.getSequenceNumber()).thenReturn(3);
        when(trip.getStatus()).thenReturn(TripStatus.IN_PROGRESS);
        when(principal.driverId()).thenReturn(42L);
        when(trips.findLockedByIdAndDriverId(99L, 42L)).thenReturn(Optional.of(trip));
        var vehicle = org.mockito.Mockito.mock(com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity.class);
        when(trip.getVehicle()).thenReturn(vehicle);
        when(vehicle.getSeatCapacity()).thenReturn(10);

        assertThatThrownBy(() -> service().confirmBoardingCount(principal, 99L, 2, 3))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verify(visit, never()).confirmEmployeeBoarding(anyInt());
    }

    private void setupTrip() {
        when(principal.driverId()).thenReturn(42L);
        when(trips.findLockedByIdAndDriverId(99L, 42L)).thenReturn(Optional.of(trip));
        when(trip.getStatus()).thenReturn(TripStatus.IN_PROGRESS);
        when(trip.getAttemptNumber()).thenReturn(1);
        when(trip.getStops()).thenReturn(List.of(firstStop, finalStop));
        when(firstStop.getSequenceNumber()).thenReturn(1);
        when(finalStop.getSequenceNumber()).thenReturn(2);
    }

    private DriverPortalService service() {
        return new DriverPortalService(trips, schedules, visits, clock);
    }
}
