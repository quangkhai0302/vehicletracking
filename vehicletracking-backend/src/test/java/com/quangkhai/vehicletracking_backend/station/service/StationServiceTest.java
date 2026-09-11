package com.quangkhai.vehicletracking_backend.station.service;

import com.quangkhai.vehicletracking_backend.station.dto.StationResponse;
import com.quangkhai.vehicletracking_backend.station.dto.StationUpsertRequest;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    @Mock
    private StationRepository stationRepository;

    private StationService stationService;

    @BeforeEach
    void setUp() {
        stationService = new StationService(stationRepository);
    }

    @Test
    void findAll_returnsActiveStationResponses() {
        StationEntity entity1 = createEntity(1L, "Trạm A", "Địa chỉ A", new BigDecimal("10.800000"), new BigDecimal("106.700000"), 50);
        StationEntity entity2 = createEntity(2L, "Trạm B", "Địa chỉ B", new BigDecimal("10.810000"), new BigDecimal("106.710000"), 100);
        when(stationRepository.findAllByActiveTrueOrderByNameAscIdAsc()).thenReturn(List.of(entity1, entity2));

        List<StationResponse> results = stationService.findAll();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(1L);
        assertThat(results.get(0).name()).isEqualTo("Trạm A");
        assertThat(results.get(1).id()).isEqualTo(2L);
        assertThat(results.get(1).name()).isEqualTo("Trạm B");
    }

    @Test
    void findById_whenStationExists_returnsStationResponse() {
        StationEntity entity = createEntity(1L, "Trạm A", "Địa chỉ A", new BigDecimal("10.800000"), new BigDecimal("106.700000"), 50);
        when(stationRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(entity));

        StationResponse response = stationService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Trạm A");
        assertThat(response.address()).isEqualTo("Địa chỉ A");
        assertThat(response.checkinRadiusMeters()).isEqualTo(50);
        assertThat(response.active()).isTrue();
    }

    @Test
    void findById_whenStationNotFound_throwsResponseStatusExceptionNotFound() {
        when(stationRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stationService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("Station 99 was not found");
                });
    }

    @Test
    void create_normalizesWhitespace_andSavesEntity() {
        StationUpsertRequest request = new StationUpsertRequest(
                "  Bến xe Miền Đông  ",
                "  292 Đinh Bộ Lĩnh, Bình Thạnh  ",
                new BigDecimal("10.801234"),
                new BigDecimal("106.710123"),
                80
        );

        when(stationRepository.save(any(StationEntity.class))).thenAnswer(invocation -> {
            StationEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        StationResponse response = stationService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Bến xe Miền Đông");
        assertThat(response.address()).isEqualTo("292 Đinh Bộ Lĩnh, Bình Thạnh");
        assertThat(response.checkinRadiusMeters()).isEqualTo(80);

        ArgumentCaptor<StationEntity> captor = ArgumentCaptor.forClass(StationEntity.class);
        verify(stationRepository).save(captor.capture());
        StationEntity captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("Bến xe Miền Đông");
        assertThat(captured.getAddress()).isEqualTo("292 Đinh Bộ Lĩnh, Bình Thạnh");
    }

    @Test
    void create_whenAddressIsBlank_normalizesToNull() {
        StationUpsertRequest request = new StationUpsertRequest(
                "Trạm Chợ Bến Thành",
                "   ",
                new BigDecimal("10.772123"),
                new BigDecimal("106.698123"),
                50
        );

        when(stationRepository.save(any(StationEntity.class))).thenAnswer(invocation -> {
            StationEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            ReflectionTestUtils.setField(saved, "createdAt", Instant.now());
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.now());
            return saved;
        });

        StationResponse response = stationService.create(request);

        assertThat(response.address()).isNull();

        ArgumentCaptor<StationEntity> captor = ArgumentCaptor.forClass(StationEntity.class);
        verify(stationRepository).save(captor.capture());
        assertThat(captor.getValue().getAddress()).isNull();
    }

    @Test
    void update_whenStationExists_updatesDetailsAndReturnsResponse() {
        StationEntity existing = createEntity(1L, "Tên cũ", "Địa chỉ cũ", new BigDecimal("10.800000"), new BigDecimal("106.700000"), 50);
        when(stationRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));

        StationUpsertRequest request = new StationUpsertRequest(
                "  Tên mới  ",
                "  Địa chỉ mới  ",
                new BigDecimal("10.850000"),
                new BigDecimal("106.750000"),
                200
        );

        StationResponse response = stationService.update(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Tên mới");
        assertThat(response.address()).isEqualTo("Địa chỉ mới");
        assertThat(response.latitude()).isEqualTo(new BigDecimal("10.850000"));
        assertThat(response.longitude()).isEqualTo(new BigDecimal("106.750000"));
        assertThat(response.checkinRadiusMeters()).isEqualTo(200);

        assertThat(existing.getName()).isEqualTo("Tên mới");
        assertThat(existing.getAddress()).isEqualTo("Địa chỉ mới");
        assertThat(existing.getCheckinRadiusMeters()).isEqualTo(200);
    }

    @Test
    void update_whenStationNotFound_throwsResponseStatusExceptionNotFound() {
        when(stationRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        StationUpsertRequest request = new StationUpsertRequest(
                "Tên mới",
                "Địa chỉ mới",
                new BigDecimal("10.850000"),
                new BigDecimal("106.750000"),
                100
        );

        assertThatThrownBy(() -> stationService.update(99L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void delete_whenStationExists_deactivatesStation() {
        StationEntity existing = createEntity(1L, "Trạm A", "Địa chỉ A", new BigDecimal("10.800000"), new BigDecimal("106.700000"), 50);
        when(stationRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(existing));

        stationService.delete(1L);

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void delete_whenStationNotFound_throwsResponseStatusExceptionNotFound() {
        when(stationRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stationService.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    private StationEntity createEntity(
            Long id,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radius
    ) {
        StationEntity entity = new StationEntity(name, address, latitude, longitude, radius);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
        return entity;
    }
}
