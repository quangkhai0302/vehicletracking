package com.quangkhai.vehicletracking_backend.driverportal;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.driverportal.service.DriverPortalService;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchRepository;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
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
    @Mock TripDispatchRepository dispatches;
    @Mock Clock clock;
    @Mock UserAccountPrincipal principal;

    @Test
    void tripsAlwaysUsesAuthenticatedDriverScope() {
        when(principal.driverId()).thenReturn(42L);
        when(trips.findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(42L)).thenReturn(List.of());
        DriverPortalService service = new DriverPortalService(trips, schedules, dispatches, clock);

        assertThat(service.trips(principal, null, null, null)).isEmpty();
        verify(trips).findAllByDriverIdOrderByScheduledDepartureAtDescIdDesc(42L);
        verifyNoMoreInteractions(trips);
    }

    @Test
    void tripDoesNotExposeAnIdOutsideAuthenticatedDriverScope() {
        when(principal.driverId()).thenReturn(42L);
        when(trips.findByIdAndDriverId(99L, 42L)).thenReturn(Optional.empty());
        DriverPortalService service = new DriverPortalService(trips, schedules, dispatches, clock);

        assertThatThrownBy(() -> service.trip(principal, 99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verify(trips).findByIdAndDriverId(99L, 42L);
        verify(trips, never()).findById(99L);
    }

    @Test
    void rejectsAccountWithoutLinkedDriver() {
        when(principal.driverId()).thenReturn(null);
        DriverPortalService service = new DriverPortalService(trips, schedules, dispatches, clock);

        assertThatThrownBy(() -> service.trips(principal, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(trips, schedules);
    }
}
