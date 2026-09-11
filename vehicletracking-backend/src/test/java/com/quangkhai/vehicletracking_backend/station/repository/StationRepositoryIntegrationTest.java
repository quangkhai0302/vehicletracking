package com.quangkhai.vehicletracking_backend.station.repository;

import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Transactional
class StationRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private StationRepository stationRepository;

    @Test
    void createUpdateAndDeactivate_persistsExpectedStationState() {
        StationEntity created = stationRepository.saveAndFlush(new StationEntity(
                "Bến xe Miền Đông",
                "292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM",
                new BigDecimal("10.801234"),
                new BigDecimal("106.710123"),
                50
        ));

        assertThat(created.getId()).isPositive();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getLatitude()).isEqualByComparingTo("10.801234");
        List<StationEntity> activeStations = stationRepository.findAllByActiveTrueOrderByNameAscIdAsc();
        assertThat(activeStations).hasSize(1);
        assertThat(activeStations.getFirst().getId()).isEqualTo(created.getId());

        created.updateDetails(
                "Bến xe Miền Đông mới",
                null,
                new BigDecimal("10.802000"),
                new BigDecimal("106.711000"),
                80
        );
        StationEntity updated = stationRepository.saveAndFlush(created);

        assertThat(updated.getName()).isEqualTo("Bến xe Miền Đông mới");
        assertThat(updated.getAddress()).isNull();
        assertThat(updated.getCheckinRadiusMeters()).isEqualTo(80);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());

        updated.deactivate();
        stationRepository.saveAndFlush(updated);
        assertThat(stationRepository.findByIdAndActiveTrue(created.getId())).isEmpty();
        assertThat(stationRepository.findAllByActiveTrueOrderByNameAscIdAsc()).isEmpty();
    }
}
