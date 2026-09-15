package com.quangkhai.vehicletracking_backend.traffic.eta;

import com.quangkhai.vehicletracking_backend.checkin.repository.TripStopVisitRepository;
import com.quangkhai.vehicletracking_backend.config.HereTrafficProperties;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.reroute.service.TripRouteGeometryService;
import com.quangkhai.vehicletracking_backend.route.entity.RouteSectionEntity;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.traffic.*;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrafficSimulationSpeedTest {
    @Test void realMatcherAndRateFollow37KmhFlowRatherThan15KmhCap() {
        var trips=mock(TripRepository.class);var visits=mock(TripStopVisitRepository.class);
        var positions=mock(VehiclePositionRepository.class);var traffic=mock(TrafficQueryService.class);
        var geometry=new TripRouteGeometryService(mock(TripRouteRevisionRepository.class));
        var now=Instant.now();
        var service=new TrafficEtaService(trips,visits,positions,traffic,new HereTrafficProperties(),Clock.fixed(now,ZoneOffset.UTC),geometry);
        var a=TripFixtures.station("A");var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L);ReflectionTestUtils.setField(b,"id",2L);
        var route=SimulationFixtures.route(a,b);
        var first=route.getSections().getFirst();
        route.getSections().set(0,new RouteSectionEntity(1,2,first.getEncodedPolyline(),156L,56L,56L));
        var vehicle=new VehicleEntity("TEST-1","Test",null);ReflectionTestUtils.setField(vehicle,"id",1L);
        var trip=new TripEntity(vehicle,route,now);ReflectionTestUtils.setField(trip,"id",1L);
        when(trips.findById(1L)).thenReturn(Optional.of(trip));
        var frame=geometry.resolve(trip).motion().at(10);
        var sample=new TelemetrySampleEntity(new TelemetryRequest(UUID.randomUUID(),1L,1L,now,
            frame.latitude(),frame.longitude(),frame.speedKmh(),frame.heading(),0d,TelemetrySource.SIMULATOR),now,now.plusSeconds(10));
        when(positions.findById(1L)).thenReturn(Optional.of(new VehiclePositionEntity(1L,sample)));
        var flow=new TrafficFlowSegment("local","Local road",156,List.of(List.of(10.77,106.70),List.of(10.771,106.701)),37,36,0,"open",1d);
        when(traffic.flowForEta(any())).thenReturn(new TrafficEnvelope<>(TrafficSource.HERE_LIVE,TrafficStatus.AVAILABLE,now,now,60,null,List.of(flow)));
        when(traffic.incidentsForEta(any())).thenReturn(new TrafficEnvelope<>(TrafficSource.HERE_LIVE,TrafficStatus.AVAILABLE,now,now,60,null,List.of()));
        assertThat(frame.speedKmh()).isCloseTo(10,within(.1));
        assertThat(frame.speedKmh()*service.simulationRate(1,80)).isCloseTo(37,within(.001));
    }
}
