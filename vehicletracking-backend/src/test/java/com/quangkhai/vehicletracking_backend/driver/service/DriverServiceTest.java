package com.quangkhai.vehicletracking_backend.driver.service;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverUpsertRequest;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {
    @Mock DriverRepository drivers;
    @Mock VehicleRepository vehicles;
    @Mock TripRepository trips;
    @InjectMocks DriverService service;

    @Test
    void create_normalizesFields() {
        when(drivers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.create(new DriverUpsertRequest(" Nguyễn Văn A ", "+84 901 234 567", " b2-123.45 "));

        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.phoneNumber()).isEqualTo("+84 901 234 567");
        assertThat(response.licenseNumber()).isEqualTo("B2-123.45");
        verify(drivers).existsByLicenseNumberAndIdNot("B2-123.45", -1L);
    }

    @Test
    void duplicateIncludingInactive_isConflict() {
        when(drivers.existsByLicenseNumberAndIdNot("B2-123", -1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new DriverUpsertRequest("A", "0901234567", "b2-123")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        verify(drivers, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicate_isControlledConflict() {
        when(drivers.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create(new DriverUpsertRequest("A", "0901234567", "B2-123")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void deactivate_requiresNoActiveAssignment() {
        var driver = new DriverEntity("A", "0901234567", "B2-123");
        when(drivers.findLockedById(1L)).thenReturn(Optional.of(driver));
        when(vehicles.existsByDriverIdAndActiveTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(1L)).isInstanceOf(ResponseStatusException.class);
        assertThat(driver.isActive()).isTrue();
    }

    @Test
    void deactivate_isSoftAndIdempotent() {
        var driver = new DriverEntity("A", "0901234567", "B2-123");
        when(drivers.findLockedById(1L)).thenReturn(Optional.of(driver));

        service.deactivate(1L);
        service.deactivate(1L);

        assertThat(driver.isActive()).isFalse();
        verify(drivers, never()).delete(any());
    }

    @Test
    void updateAvatar_acceptsValidatedPngAndCanClearIt() throws IOException {
        var driver = new DriverEntity("A", "0901234567", "B2-123");
        when(drivers.findLockedById(1L)).thenReturn(Optional.of(driver));
        when(drivers.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        service.updateAvatar(1L, new MockMultipartFile("file", "driver.png", "image/png", bytes.toByteArray()));

        assertThat(driver.hasAvatar()).isTrue();
        assertThat(driver.avatarContentType()).isEqualTo("image/png");
        service.clearAvatar(1L);
        assertThat(driver.hasAvatar()).isFalse();
    }

    @Test
    void updateAvatar_rejectsUnsupportedType() {
        var driver = new DriverEntity("A", "0901234567", "B2-123");
        when(drivers.findLockedById(1L)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> service.updateAvatar(1L,
                new MockMultipartFile("file", "driver.svg", "image/svg+xml", "<svg/>".getBytes())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(415));
        verify(drivers, never()).saveAndFlush(any());
    }
}
