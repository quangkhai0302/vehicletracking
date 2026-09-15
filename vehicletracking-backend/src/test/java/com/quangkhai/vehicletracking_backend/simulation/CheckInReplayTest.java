package com.quangkhai.vehicletracking_backend.simulation;

import com.quangkhai.vehicletracking_backend.checkin.entity.*;
import com.quangkhai.vehicletracking_backend.checkin.repository.*;
import com.quangkhai.vehicletracking_backend.checkin.service.CheckInService;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.telemetry.dto.TelemetryRequest;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.trip.TripFixtures;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckInReplayTest {
    @Test void firstSampleOfReplayCannotUsePreviousAttemptsTrace() {
        var now=Instant.parse("2026-09-15T02:00:00Z");
        var a=TripFixtures.station("A"); var b=TripFixtures.station("B");
        ReflectionTestUtils.setField(a,"id",1L); ReflectionTestUtils.setField(b,"id",2L);
        var route=SimulationFixtures.route(a,b); ReflectionTestUtils.setField(route,"id",7L);
        var trip=new TripEntity(new VehicleEntity("TEST123","Test",null),route,now.minusSeconds(60));
        ReflectionTestUtils.setField(trip,"id",5L);
        RouteDetailResponse.from(route).stops().forEach(stop->trip.addStop(new TripStopEntity(stop,50,now.minusSeconds(60))));
        var old=sample(1,now.minusSeconds(10),a.getLatitude().doubleValue(),a.getLongitude().doubleValue());
        var state=new TripCheckInStateEntity(trip,null,false,old,100);
        trip.replay(now); trip.start(now); state.replay(trip.getAttemptNumber());
        var current=sample(2,now,a.getLatitude().doubleValue(),a.getLongitude().doubleValue());
        current.assignAttempt(trip.getAttemptNumber());
        var states=mock(TripCheckInStateRepository.class); var visits=mock(TripStopVisitRepository.class);
        when(states.findLockedByTripId(5L)).thenReturn(Optional.of(state));
        var service=new CheckInService(states,visits);
        ReflectionTestUtils.setField(service,"entityManager",mock(EntityManager.class));

        service.process(trip,old,current);
        var saved=ArgumentCaptor.forClass(TripStopVisitEntity.class);
        verify(visits).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(saved.getValue().getStopSequence()).isEqualTo(1);
        assertThat(saved.getValue().getFromSample()).isNull();
        assertThat(saved.getValue().getEvidenceKind()).isEqualTo(CheckInEvidenceKind.POINT);
        assertThat(state.getLastSample()).isSameAs(current);
        assertThat(state.getRevision()).isEqualTo(102);
        service.process(trip,old,current);
        verify(visits,times(1)).saveAndFlush(any());
    }

    private TelemetrySampleEntity sample(long id, Instant time,double lat,double lng) {
        var sample=new TelemetrySampleEntity(new TelemetryRequest(UUID.randomUUID(),1L,5L,time,lat,lng,0d,0d,0d,
            TelemetrySource.SIMULATOR),time,time);
        ReflectionTestUtils.setField(sample,"id",id);
        return sample;
    }
}
