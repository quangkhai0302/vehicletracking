package com.quangkhai.vehicletracking_backend.route.service;
import com.quangkhai.vehicletracking_backend.route.dto.*;
import com.quangkhai.vehicletracking_backend.route.provider.*;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.simulation.SimulationFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteShapeServiceTest {
    RouteRepository routes=mock(RouteRepository.class);TripRepository trips=mock(TripRepository.class);
    RoutingProvider provider=mock(RoutingProvider.class);
    RouteShapeService service=new RouteShapeService(routes,trips,provider);
    private com.quangkhai.vehicletracking_backend.route.entity.RouteEntity source() {
        var a=TripFixtures.station("A");var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L);ReflectionTestUtils.setField(b,"id",2L);
        var route=SimulationFixtures.route(a,b);ReflectionTestUtils.setField(route,"id",3L);
        when(routes.findById(3L)).thenReturn(Optional.of(route));when(routes.findLockedById(3L)).thenReturn(Optional.of(route));
        return route;
    }
    private RouteShapeRequest input() { return new RouteShapeRequest(List.of(new RouteShapeRequest.ShapePoint(2,new BigDecimal("10.771"),new BigDecimal("106.702")))); }
    @Test void previewRemapsShapingWaypointsToOriginalStopsAndDoesNotWrite() {
        var source=source();
        when(provider.calculate(any())).thenAnswer(call -> {
            List<RoutingWaypoint> points=call.getArgument(0);
            assertThat(points).hasSize(4);assertThat(points.get(1).stationId()).isNull();assertThat(points.get(1).dwellDurationSeconds()).isZero();
            return new CalculatedRoute(Instant.now(),List.of(new CalculatedSection(1,2,"first",10,10,10),
                new CalculatedSection(2,3,"second",20,20,20),new CalculatedSection(3,4,"third",30,30,30)));
        });
        var preview=service.preview(3,input());
        assertThat(preview.stops()).hasSize(3);
        assertThat(preview.sections()).extracting(RouteDetailResponse.RouteSectionResponse::destinationStopSequence).containsExactly(2,2,3);
        assertThat(preview.totalDwellDurationSeconds()).isEqualTo(4);
        assertThat(preview.shapingPoints()).hasSize(1);
        assertThat(source.getSections()).hasSize(2);
        verify(routes,never()).saveAndFlush(any());verify(routes,never()).flush();
    }
    @Test void invalidLegAndUsedRouteAreRejectedBeforeProviderCall() {
        source();when(trips.existsByRouteId(3L)).thenReturn(true);
        assertThatThrownBy(() -> service.save(3,input(),false)).isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> service.preview(3,new RouteShapeRequest(List.of(new RouteShapeRequest.ShapePoint(8,BigDecimal.ZERO,BigDecimal.ZERO)))))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(provider);
    }
    @Test void providerFailureLeavesExistingGeometryAndPointsIntact() {
        var source=source();var section=source.getSections().getFirst();
        when(provider.calculate(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThatThrownBy(() -> service.save(3,input(),false)).isInstanceOf(IllegalStateException.class);
        assertThat(source.getSections().getFirst()).isSameAs(section);verify(routes,never()).flush();
    }
    @Test void copyOfUsedRouteLeavesOriginalUntouchedAndKeepsShapingPoints() {
        var source=source();var originalSection=source.getSections().getFirst();
        when(trips.existsByRouteId(3L)).thenReturn(true);
        when(provider.calculate(any())).thenReturn(new CalculatedRoute(Instant.now(),List.of(
            new CalculatedSection(1,2,"first",10,10,10),new CalculatedSection(2,3,"second",20,20,20),
            new CalculatedSection(3,4,"third",30,30,30))));
        when(routes.saveAndFlush(any())).thenAnswer(call -> {
            com.quangkhai.vehicletracking_backend.route.entity.RouteEntity copy=call.getArgument(0);
            assertThat(copy).isNotSameAs(source);ReflectionTestUtils.setField(copy,"id",4L);return copy;
        });
        var copy=service.save(3,input(),true);
        assertThat(copy.id()).isEqualTo(4L);assertThat(copy.shapingPoints()).hasSize(1);
        assertThat(source.getSections().getFirst()).isSameAs(originalSection);
        assertThat(source.getShapingPoints()).isEmpty();verify(routes,never()).flush();
    }
}
