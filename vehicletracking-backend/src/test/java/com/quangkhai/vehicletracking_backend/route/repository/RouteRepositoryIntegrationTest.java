package com.quangkhai.vehicletracking_backend.route.repository;

import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteStopEntity;
import com.quangkhai.vehicletracking_backend.route.entity.RouteTransportMode;
import com.quangkhai.vehicletracking_backend.route.entity.RoutingProviderName;
import com.quangkhai.vehicletracking_backend.station.entity.StationEntity;
import com.quangkhai.vehicletracking_backend.station.repository.StationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@Transactional
class RouteRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shapingPointsPersistAndCanBeReplacedWithoutSequenceConflict() {
        var a=stationRepository.saveAndFlush(new StationEntity("A",null,new BigDecimal("10.77"),new BigDecimal("106.7"),50));
        var b=stationRepository.saveAndFlush(new StationEntity("B",null,new BigDecimal("10.771"),new BigDecimal("106.701"),50));
        var route=com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures.route(a,b);
        route.addShapingPoint(new com.quangkhai.vehicletracking_backend.route.entity.RouteShapePointEntity(1,2,
            new BigDecimal("10.770500"),new BigDecimal("106.702000")));
        long id=routeRepository.saveAndFlush(route).getId();entityManager.clear();
        var found=routeRepository.findById(id).orElseThrow();
        assertThat(found.getShapingPoints()).hasSize(1);
        assertThat(found.getShapingPoints().getFirst().getDestinationStopSequence()).isEqualTo(2);
        found.getShapingPoints().clear();routeRepository.flush();
        found.addShapingPoint(new com.quangkhai.vehicletracking_backend.route.entity.RouteShapePointEntity(1,2,
            new BigDecimal("10.770600"),new BigDecimal("106.703000")));
        routeRepository.saveAndFlush(found);entityManager.clear();
        assertThat(routeRepository.findById(id).orElseThrow().getShapingPoints().getFirst().getLongitude())
            .isEqualByComparingTo("106.703000");
    }

    @Test
    void persistRouteWithStopsAndSections_persistsAndRetrievesGraphCorrectly() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity(
                "Bến xe Miền Đông", "292 Đinh Bộ Lĩnh", new BigDecimal("10.801234"), new BigDecimal("106.710123"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity(
                "Ngã tư Hàng Xanh", "Điện Biên Phủ", new BigDecimal("10.800100"), new BigDecimal("106.711100"), 50));
        StationEntity s3 = stationRepository.saveAndFlush(new StationEntity(
                "Chợ Bến Thành", "Lê Lợi", new BigDecimal("10.772123"), new BigDecimal("106.698123"), 50));

        Instant now = Instant.now();
        RouteEntity route = new RouteEntity(
                "Tuyến Miền Đông - Bến Thành",
                RouteTransportMode.CAR,
                RoutingProviderName.HERE,
                8200L,
                1260L,
                1050L,
                120L,
                1380L,
                now,
                now
        );

        route.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        route.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 120));
        route.addStop(new RouteStopEntity(s3, 3, s3.getName(), s3.getLatitude(), s3.getLongitude(), 0));

        route.addSection(new RouteSectionEntity(1, 2, "encoded_polyline_1", 3100L, 480L, 410L));
        route.addSection(new RouteSectionEntity(2, 3, "encoded_polyline_2", 5100L, 780L, 640L));

        RouteEntity saved = routeRepository.saveAndFlush(route);
        assertThat(saved.getId()).isNotNull();

        entityManager.clear();

        RouteEntity found = routeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Tuyến Miền Đông - Bến Thành");
        assertThat(found.getTotalDistanceMeters()).isEqualTo(8200L);
        assertThat(found.getEstimatedTripDurationSeconds()).isEqualTo(1380L);
        assertThat(found.getStops()).hasSize(3);
        assertThat(found.getStops().get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(found.getStops().get(0).getStationNameSnapshot()).isEqualTo("Bến xe Miền Đông");
        assertThat(found.getStops().get(1).getDwellDurationSeconds()).isEqualTo(120);
        assertThat(found.getSections()).hasSize(2);
        assertThat(found.getSections().get(0).getDestinationStopSequence()).isEqualTo(2);
        assertThat(found.getSections().get(1).getDestinationStopSequence()).isEqualTo(3);
    }

    @Test
    void findAllByOrderByCreatedAtDescIdDesc_returnsLatestFirst() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r1 = new RouteEntity("Route 1", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r1.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r1.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r1.addSection(new RouteSectionEntity(1, 2, "poly1", 1000L, 100L, 90L));
        routeRepository.saveAndFlush(r1);

        RouteEntity r2 = new RouteEntity("Route 2", RouteTransportMode.CAR, RoutingProviderName.HERE, 2000L, 200L, 180L, 0L, 200L, now.plusSeconds(60), now.plusSeconds(60));
        r2.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r2.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r2.addSection(new RouteSectionEntity(1, 2, "poly2", 2000L, 200L, 180L));
        routeRepository.saveAndFlush(r2);

        List<RouteEntity> list = routeRepository.findAllByOrderByCreatedAtDescIdDesc();
        assertThat(list).hasSizeGreaterThanOrEqualTo(2);
        assertThat(list.get(0).getId()).isEqualTo(r2.getId());
        assertThat(list.get(1).getId()).isEqualTo(r1.getId());
    }

    @Test
    void duplicateStopSequence_throwsDataIntegrityViolationException() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Duplicate Stop Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 1, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0)); // duplicate sequence 1
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSectionSequence_throwsDataIntegrityViolationException() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Duplicate Section Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r.addSection(new RouteSectionEntity(1, 2, "poly1", 500L, 50L, 45L));
        r.addSection(new RouteSectionEntity(1, 2, "poly2", 500L, 50L, 45L)); // duplicate section_sequence 1

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteStationReferencedByRouteStop_isRestricted() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));
        routeRepository.saveAndFlush(r);

        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM vehicle_tracking.stations WHERE id = :id")
                    .setParameter("id", s1.getId())
                    .executeUpdate();
        }).hasCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void stopDwellDuration_negative_violatesCheckConstraint() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Negative Dwell Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), -1));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stopDwellDuration_exceeds3600_violatesCheckConstraint() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Excessive Dwell Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 3601));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stopLatitude_outOfBounds_violatesCheckConstraint() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Invalid Lat Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), new BigDecimal("91.000000"), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stopLongitude_outOfBounds_violatesCheckConstraint() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Invalid Lng Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), new BigDecimal("181.000000"), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sectionDestinationStop_compositeFkViolation_throwsException() {
        entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();

        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Invalid FK Section Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        // destination_stop_sequence is 99 which does not exist in stops
        r.addSection(new RouteSectionEntity(1, 99, "poly", 1000L, 100L, 90L));

        assertThatThrownBy(() -> routeRepository.saveAndFlush(r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteRoute_cascadesDeletionToStopsAndSections() {
        StationEntity s1 = stationRepository.saveAndFlush(new StationEntity("S1", "Addr1", new BigDecimal("10.8"), new BigDecimal("106.7"), 50));
        StationEntity s2 = stationRepository.saveAndFlush(new StationEntity("S2", "Addr2", new BigDecimal("10.9"), new BigDecimal("106.8"), 50));

        Instant now = Instant.now();
        RouteEntity r = new RouteEntity("Cascade Delete Route", RouteTransportMode.CAR, RoutingProviderName.HERE, 1000L, 100L, 90L, 0L, 100L, now, now);
        r.addStop(new RouteStopEntity(s1, 1, s1.getName(), s1.getLatitude(), s1.getLongitude(), 0));
        r.addStop(new RouteStopEntity(s2, 2, s2.getName(), s2.getLatitude(), s2.getLongitude(), 0));
        r.addSection(new RouteSectionEntity(1, 2, "poly", 1000L, 100L, 90L));
        RouteEntity saved = routeRepository.saveAndFlush(r);
        Long routeId = saved.getId();

        entityManager.clear();

        // Verify inserted
        Number stopCount = (Number) entityManager.createNativeQuery("SELECT count(*) FROM vehicle_tracking.route_stops WHERE route_id = :routeId")
                .setParameter("routeId", routeId)
                .getSingleResult();
        Number sectionCount = (Number) entityManager.createNativeQuery("SELECT count(*) FROM vehicle_tracking.route_sections WHERE route_id = :routeId")
                .setParameter("routeId", routeId)
                .getSingleResult();
        assertThat(stopCount.longValue()).isEqualTo(2);
        assertThat(sectionCount.longValue()).isEqualTo(1);

        // Delete route
        routeRepository.deleteById(routeId);
        routeRepository.flush();
        entityManager.clear();

        Number stopCountAfter = (Number) entityManager.createNativeQuery("SELECT count(*) FROM vehicle_tracking.route_stops WHERE route_id = :routeId")
                .setParameter("routeId", routeId)
                .getSingleResult();
        Number sectionCountAfter = (Number) entityManager.createNativeQuery("SELECT count(*) FROM vehicle_tracking.route_sections WHERE route_id = :routeId")
                .setParameter("routeId", routeId)
                .getSingleResult();
        assertThat(stopCountAfter.longValue()).isZero();
        assertThat(sectionCountAfter.longValue()).isZero();
    }
}
