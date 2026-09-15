package com.quangkhai.vehicletracking_backend.vehicle.service;

import com.quangkhai.vehicletracking_backend.vehicle.dto.VehicleUpsertRequest;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleType;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {
    @Mock VehicleRepository vehicles;
    @Mock TripRepository trips;
    @InjectMocks VehicleService service;
    @Test void create_normalizesPlateAndTrimsFields() {
        when(vehicles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.create(new VehicleUpsertRequest(" 51b-123.45 ", " Xe A ", "  "));
        assertThat(response.plateNumber()).isEqualTo("51B12345");
        assertThat(response.name()).isEqualTo("Xe A");
        assertThat(response.description()).isNull();
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR);
        verify(vehicles).existsByPlateNumberAndIdNot("51B12345", -1L);
    }
    @Test void createAndUpdate_preserveSelectedVehicleType() {
        when(vehicles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var created = service.create(new VehicleUpsertRequest("59X112345", "Xe giao hàng", null, VehicleType.MOTORCYCLE));
        assertThat(created.vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);

        var vehicle = new VehicleEntity("59X112345", "Xe giao hàng", null, VehicleType.MOTORCYCLE);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        var updated = service.update(1L, new VehicleUpsertRequest("59X112345", "Xe giao hàng", null, VehicleType.CAR));
        assertThat(updated.vehicleType()).isEqualTo(VehicleType.CAR);
    }
    @Test void duplicateIncludingInactive_isConflict() {
        when(vehicles.existsByPlateNumberAndIdNot("51B12345", -1L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new VehicleUpsertRequest("51b-123.45", "A", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        verify(vehicles, never()).saveAndFlush(any());
    }
    @Test void concurrentDuplicate_isControlledConflict() {
        when(vehicles.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertThatThrownBy(() -> service.create(new VehicleUpsertRequest("51B12345", "A", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
    @Test void separatorsOnly_isRejected() {
        assertThatThrownBy(() -> service.create(new VehicleUpsertRequest("---...", "A", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
    @Test void deactivate_requiresAllTripsTerminal() {
        var vehicle = new VehicleEntity("51B12345", "A", null);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(trips.existsByVehicleIdAndStatusIn(eq(1L), any())).thenReturn(true);
        assertThatThrownBy(() -> service.deactivate(1)).isInstanceOf(ResponseStatusException.class);
        assertThat(vehicle.isActive()).isTrue();
    }
    @Test void deactivate_isSoftAndIdempotent() {
        var vehicle = new VehicleEntity("51B12345", "A", null);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        service.deactivate(1); var timestamp = vehicle.getUpdatedAt();
        service.deactivate(1);
        assertThat(vehicle.isActive()).isFalse();
        assertThat(vehicle.getUpdatedAt()).isEqualTo(timestamp);
        verify(vehicles, never()).delete(any());
    }
    @Test void update_excludesOwnIdFromUniquenessAndKeepsIdentity() {
        var vehicle = new VehicleEntity("51B12345", "A", null);
        when(vehicles.findLockedById(1L)).thenReturn(Optional.of(vehicle));
        when(vehicles.saveAndFlush(vehicle)).thenReturn(vehicle);
        var result = service.update(1, new VehicleUpsertRequest("51b-12345", " New ", " Note "));
        assertThat(result.name()).isEqualTo("New");
        assertThat(result.description()).isEqualTo("Note");
        verify(vehicles).existsByPlateNumberAndIdNot("51B12345", 1L);
    }
    @Test void missingVehicle_isNotFound() {
        assertThatThrownBy(() -> service.findById(99)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
}
