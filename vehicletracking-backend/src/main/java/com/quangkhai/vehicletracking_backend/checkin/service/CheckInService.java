package com.quangkhai.vehicletracking_backend.checkin.service;

import com.quangkhai.vehicletracking_backend.checkin.entity.*;
import com.quangkhai.vehicletracking_backend.checkin.geometry.GeofenceCrossing;
import com.quangkhai.vehicletracking_backend.checkin.repository.*;
import com.quangkhai.vehicletracking_backend.route.dto.RouteDetailResponse;
import com.quangkhai.vehicletracking_backend.simulation.motion.RouteMotion;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.trip.entity.*;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Automatic, ordered stop detection. It is called inside TelemetryService's
 * transaction, so a committed sample always has a durable detector checkpoint. */
@Service @RequiredArgsConstructor
public class CheckInService {
    static final long MAX_GAP_SECONDS = 15;
    static final double MAX_SEGMENT_METERS = 1_000d;
    static final double MAX_INFERRED_SPEED_KMH = 160d;
    static final double MAX_ACCURACY_METERS = 30d;
    private final TripCheckInStateRepository states;
    private final TripStopVisitRepository visits;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void process(TripEntity trip, TelemetrySampleEntity previous, TelemetrySampleEntity current) {
        var state=states.findLockedByTripId(trip.getId()).orElse(null);
        if (state==null) {
            // The state uses the trip id as an assigned @MapsId key. Persist it
            // explicitly; repository.save() would choose merge() for the
            // non-null id and Hibernate cannot merge this transient one-to-one.
            // The newly accepted sample is the first processed sample. Keep
            // the pre-processing checkpoint at revision 0 so detect() can
            // advance the durable revision exactly once for this sample.
            state=new TripCheckInStateEntity(trip,1,false,current,0);
            entityManager.persist(state);
            entityManager.flush();
            detect(trip,state,null,current);
            return;
        }
        if (state.getLastSample()!=null && state.getLastSample().getId().equals(current.getId())) return;
        detect(trip,state,usablePrevious(trip,previous,state.getLastSample()),current);
    }

    private TelemetrySampleEntity usablePrevious(TripEntity trip, TelemetrySampleEntity candidate, TelemetrySampleEntity checkpoint) {
        if (candidate==null || checkpoint==null || !candidate.getId().equals(checkpoint.getId())) return null;
        if (!candidate.getTripId().equals(trip.getId())) return null;
        if (candidate.getAttemptNumber()!=trip.getAttemptNumber()) return null;
        return candidate;
    }

    private void detect(TripEntity trip, TripCheckInStateEntity state, TelemetrySampleEntity previous, TelemetrySampleEntity current) {
        Integer target=state.getNextStopSequence();
        boolean armed=!state.isAwaitingExit();
        double minimumFraction=-1d;
        int processed=0;
        while (target!=null && processed++ < trip.getStops().size()) {
            final int targetSequence=target;
            var stop=trip.getStops().stream().filter(item->item.getSequenceNumber().equals(targetSequence)).findFirst().orElse(null);
            if (stop==null) {
                target=null;
                break;
            }
            double radius=stop.getCheckinRadiusMeters();
            double currentDistance=GeofenceCrossing.distance(current.getLatitude(),current.getLongitude(),stop.getLatitude(),stop.getLongitude());
            if (!armed) {
                if (currentDistance>radius+hysteresis(radius)) armed=true;
                else break;
            }
            var evidence=entry(trip,stop,trip.getStartedAt(),previous,current,minimumFraction);
            if (evidence.isEmpty()) break;
            var found=evidence.get();
            saveVisit(trip,stop,current,previous,found);
            target=nextSequence(trip,target);
            minimumFraction=Math.max(minimumFraction,found.fraction());
            if (target==null) {
                armed=false;
                break;
            }
            // A POINT only proves presence at the current sample; it cannot
            // prove that a second overlapping stop was traversed in the same
            // instant. Segment/route evidence can continue in strict order.
            armed=!inside(trip,target,new GeofenceCrossing.Point(found.latitude(),found.longitude()));
            if (found.kind()==CheckInEvidenceKind.POINT) break;
        }
        state.update(current,target,target!=null && !armed,targetRevision(state));
        entityManager.flush();
    }

    private long targetRevision(TripCheckInStateEntity state) { return state.getRevision()+1; }
    private int hysteresis(double radius) { return (int)Math.ceil(Math.max(5d,radius*.1d)); }
    private boolean inside(TripEntity trip,int sequence,TelemetrySampleEntity sample) {
        return inside(trip,sequence,new GeofenceCrossing.Point(sample.getLatitude(),sample.getLongitude()));
    }
    private boolean inside(TripEntity trip,int sequence,GeofenceCrossing.Point point) {
        var stop=trip.getStops().stream().filter(item->item.getSequenceNumber()==sequence).findFirst().orElseThrow();
        return GeofenceCrossing.inside(point,stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),stop.getCheckinRadiusMeters());
    }
    private Integer nextSequence(TripEntity trip,int sequence) {
        return trip.getStops().stream().map(TripStopEntity::getSequenceNumber).filter(item->item>sequence).min(Integer::compareTo).orElse(null);
    }
    private record Evidence(double fraction,double latitude,double longitude,CheckInEvidenceKind kind) {}

    private Optional<Evidence> entry(TripEntity trip, TripStopEntity stop, Instant startedAt,
                                     TelemetrySampleEntity previous, TelemetrySampleEntity current,
                                     double minimumFraction) {
        double radius=stop.getCheckinRadiusMeters();
        var now=new GeofenceCrossing.Point(current.getLatitude(),current.getLongitude());
        if (!quality(current,stop)) return Optional.empty();
        boolean currentAfterStart=startedAt==null || !current.getRecordedAt().isBefore(startedAt);
        if (!currentAfterStart) return Optional.empty();
        if (previous==null || !quality(previous,stop) || !previous.getSource().equals(current.getSource())
                || (startedAt!=null && previous.getRecordedAt().isBefore(startedAt)))
            return pointIfInside(stop, current, radius);
        if (current.getSource()==TelemetrySource.SIMULATOR) {
            if (current.getSimulatedAt()==null || previous.getSimulatedAt()==null) return pointIfInside(stop,current,radius);
            double fromElapsed=Duration.between(trip.getScheduledDepartureAt(),previous.getSimulatedAt()).toNanos()/1_000_000_000d;
            double toElapsed=Duration.between(trip.getScheduledDepartureAt(),current.getSimulatedAt()).toNanos()/1_000_000_000d;
            if (toElapsed<=fromElapsed) return GeofenceCrossing.inside(now,stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius)
                ? Optional.of(new Evidence(1,current.getLatitude(),current.getLongitude(),CheckInEvidenceKind.POINT)) : Optional.empty();
            var crossing=new RouteMotion(RouteDetailResponse.from(trip.getRoute())).firstEntryBetween(fromElapsed,toElapsed,
                stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius,minimumFraction);
            if(crossing==null) return Optional.empty();
            return Optional.of(new Evidence(crossing.fraction(),crossing.latitude(),crossing.longitude(),CheckInEvidenceKind.ROUTE_TRACE));
        }
        var before=new GeofenceCrossing.Point(previous.getLatitude(),previous.getLongitude());
        Duration elapsed=Duration.between(previous.getRecordedAt(),current.getRecordedAt());
        long gapNanos=elapsed.toNanos();
        double distance=GeofenceCrossing.distance(before,now);
        if (gapNanos<=0) return Optional.empty();
        // A long telemetry gap cannot support interpolation, but a good current
        // point is still valid evidence. A spatial/speed jump rejects both the
        // segment and the point, so it cannot bypass the jump policy.
        if (gapNanos>Duration.ofSeconds(MAX_GAP_SECONDS).toNanos()) return pointIfInside(stop,current,radius);
        if (distance>MAX_SEGMENT_METERS || distance/(gapNanos/1_000_000_000d)*3.6>MAX_INFERRED_SPEED_KMH)
            return Optional.empty();
        boolean beforeInside=GeofenceCrossing.inside(before,stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius);
        if (beforeInside) return GeofenceCrossing.inside(now,stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius)
            ? Optional.of(new Evidence(1,current.getLatitude(),current.getLongitude(),pointKind(current))) : Optional.empty();
        var crossing=GeofenceCrossing.firstEntry(before,now,stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius);
        if(crossing==null) return Optional.empty();
        if(crossing.fraction()<=minimumFraction+1e-9) return Optional.empty();
        return Optional.of(new Evidence(crossing.fraction(),crossing.latitude(),crossing.longitude(),
            current.getSource()==TelemetrySource.SIMULATOR?CheckInEvidenceKind.ROUTE_TRACE:CheckInEvidenceKind.SEGMENT));
    }
    private Optional<Evidence> pointIfInside(TripStopEntity stop, TelemetrySampleEntity current, double radius) {
        return GeofenceCrossing.inside(new GeofenceCrossing.Point(current.getLatitude(),current.getLongitude()),
                stop.getLatitude().doubleValue(),stop.getLongitude().doubleValue(),radius)
            ? Optional.of(new Evidence(1,current.getLatitude(),current.getLongitude(),pointKind(current))) : Optional.empty();
    }
    private CheckInEvidenceKind pointKind(TelemetrySampleEntity sample) { return CheckInEvidenceKind.POINT; }
    private boolean quality(TelemetrySampleEntity sample,TripStopEntity stop) {
        double accuracy=sample.getAccuracyMeters();
        return Double.isFinite(accuracy) && accuracy>=0 && stop.getCheckinRadiusMeters()!=null
            && stop.getCheckinRadiusMeters()>0
            && accuracy<=Math.min(MAX_ACCURACY_METERS,stop.getCheckinRadiusMeters()/2d);
    }
    private void saveVisit(TripEntity trip,TripStopEntity stop,TelemetrySampleEntity current,TelemetrySampleEntity previous,Evidence evidence) {
        if (visits.existsByTripIdAndStopSequence(trip.getId(),stop.getSequenceNumber())) return;
        Instant actual=current.getRecordedAt(); Instant simulated=current.getSimulatedAt();
        Long fromId=null;
        if(previous!=null && evidence.kind()!=CheckInEvidenceKind.POINT) {
            fromId=previous.getId();
            long micros=Duration.between(previous.getRecordedAt(),current.getRecordedAt()).toNanos()/1_000;
            actual=previous.getRecordedAt().plus((long)(micros*evidence.fraction()),ChronoUnit.MICROS).truncatedTo(ChronoUnit.MICROS);
            if(simulated!=null && previous.getSimulatedAt()!=null) {
                long smicros=Duration.between(previous.getSimulatedAt(),simulated).toNanos()/1_000;
                simulated=previous.getSimulatedAt().plus((long)(smicros*evidence.fraction()),ChronoUnit.MICROS).truncatedTo(ChronoUnit.MICROS);
            }
        }
        var visit=new TripStopVisitEntity(trip,stop.getSequenceNumber(),current.getSource(),evidence.kind(),actual,simulated,
            current.getReceivedAt(),previous!=null&&evidence.kind()!=CheckInEvidenceKind.POINT?previous:null,current,evidence.fraction(),evidence.latitude(),evidence.longitude());
        visits.saveAndFlush(visit);
    }
}
