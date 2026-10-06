package com.quangkhai.vehicletracking_backend.reporting.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationPunctuality;
import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportItem;
import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportMetric;
import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportResponse;
import com.quangkhai.vehicletracking_backend.reporting.dto.SimulationReportRevision;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripRouteRevisionRepository;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationAttemptMetadata;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationRunEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationScenario;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationStatus;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationAttemptRepository;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class SimulationReportService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final SimulationRepository runs;
    private final SimulationAttemptRepository attempts;
    private final TripRepository trips;
    private final TripNotificationRepository notifications;
    private final TripRouteRevisionRepository revisions;
    private final Clock operationsClock;

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public SimulationReportResponse report(LocalDate requestedFrom, LocalDate requestedTo, Long vehicleId,
            Long driverId, SimulationReportMetric metric, int page, int size) {
        LocalDate to=requestedTo==null?operationsClock.instant().atZone(ZONE).toLocalDate():requestedTo;
        LocalDate from=requestedFrom==null?to.minusDays(29):requestedFrom;
        if(from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>=366
                || vehicleId!=null && vehicleId<1 || driverId!=null && driverId<1
                || page<0 || size<1 || size>100 || metric==null)
            throw new ResponseStatusException(BAD_REQUEST,"Khoảng ngày, bộ lọc hoặc phân trang không hợp lệ.");
        Instant start=from.atStartOfDay(ZONE).toInstant(), end=to.plusDays(1).atStartOfDay(ZONE).toInstant();
        var currentRuns=runs.findForSimulationReport(start,end,vehicleId,driverId);
        var archived=attempts.findForSimulationReport(start,end,vehicleId,driverId);
        Map<Long,TripEntity> tripById=new HashMap<>();
        trips.findAllById(currentRuns.stream().map(SimulationRunEntity::getTripId).toList())
            .forEach(trip -> tripById.put(trip.getId(),trip));
        var tripIds=new HashSet<Long>(); currentRuns.forEach(run -> tripIds.add(run.getTripId()));
        archived.forEach(attempt -> tripIds.add(attempt.getTripId()));
        Map<Key,Long> eventCounts=new HashMap<>();
        Map<Key,List<SimulationReportRevision>> routeRevisions=new HashMap<>();
        if(!tripIds.isEmpty()) {
            notifications.findSimulationOffRouteEvents(tripIds).forEach(event -> {
                var key=new Key(event.getTrip().getId(),event.getAttemptNumber());
                eventCounts.merge(key,1L,Long::sum);
            });
            revisions.findAllForSimulationReport(tripIds).forEach(revision -> {
                Integer attempt=revision.getSimulationAttemptNumber();
                if(attempt==null && revision.getComparisonSnapshot()!=null) attempt=revision.getComparisonSnapshot().attemptNumber();
                if(attempt==null) return;
                var key=new Key(revision.getTrip().getId(),attempt);
                routeRevisions.computeIfAbsent(key,ignored -> new ArrayList<>()).add(new SimulationReportRevision(
                    revision.getId(),revision.getRevisionNumber(),revision.getCreatedAt(),
                    revision.getBaselineRemainingSeconds(),revision.getRevisedRemainingSeconds()));
            });
        }
        List<SimulationReportItem> all=new ArrayList<>();
        for(var run:currentRuns) {
            var trip=tripById.get(run.getTripId()); if(trip==null) continue;
            all.add(item(run.getTripId(),trip.getAttemptNumber(),true,run.getMetadata(),
                trip.getStartedAt()==null?run.getCreatedAt():trip.getStartedAt(),trip.getEndedAt(),
                run.getStatus(),run.getScenario(),run.getVirtualElapsedSeconds(),run.getElapsedSeconds(),eventCounts,routeRevisions));
        }
        for(var attempt:archived) all.add(item(attempt.getTripId(),attempt.getAttemptNumber(),false,attempt.getMetadata(),
            attempt.getStartedAt()==null?attempt.getArchivedAt():attempt.getStartedAt(),attempt.getEndedAt(),
            attempt.getStatus(),attempt.getScenario(),attempt.getVirtualElapsedSeconds(),attempt.getElapsedSeconds(),eventCounts,routeRevisions));
        all.sort(Comparator.comparing(SimulationReportItem::startedAt,Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SimulationReportItem::tripId,Comparator.reverseOrder())
            .thenComparing(SimulationReportItem::attemptNumber,Comparator.reverseOrder()));
        long completed=all.stream().filter(i -> i.status()==SimulationStatus.COMPLETED).count();
        long knownCompleted=all.stream().filter(i -> i.status()==SimulationStatus.COMPLETED && i.metadataComplete()).count();
        long onTime=all.stream().filter(i -> i.punctuality()==SimulationPunctuality.ON_TIME).count();
        long late=all.stream().filter(i -> i.punctuality()==SimulationPunctuality.LATE).count();
        long unknown=all.stream().filter(i -> !i.metadataComplete()).count();
        double distance=all.stream().map(SimulationReportItem::plannedDistanceMeters).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double virtual=all.stream().map(SimulationReportItem::virtualElapsedSeconds).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        long offRoute=all.stream().mapToLong(SimulationReportItem::offRouteEventCount).sum();
        var filtered=all.stream().filter(i -> matches(i,metric)).toList();
        long offset=(long)page*size; int begin=(int)Math.min(filtered.size(),offset), finish=Math.min(filtered.size(),begin+size);
        return new SimulationReportResponse(from,to,operationsClock.instant(),all.size(),completed,knownCompleted,
            distance,virtual,knownCompleted==0?null:Math.round(onTime*10000d/knownCompleted)/100d,
            late,offRoute,unknown,filtered.subList(begin,finish),page,size,filtered.size(),(filtered.size()+size-1)/size);
    }

    private record Key(long tripId,int attempt) {}

    private SimulationReportItem item(long tripId,int attempt,boolean current,SimulationAttemptMetadata metadata,
            Instant fallbackStart,Instant ended,SimulationStatus status,SimulationScenario scenario,
            Double virtual,double progress,Map<Key,Long> events,Map<Key,List<SimulationReportRevision>> revisions) {
        Double baseline=metadata==null?null:metadata.getPlannedDurationSeconds();
        boolean known=metadata!=null && metadata.getAttemptStartedAt()!=null && baseline!=null && virtual!=null
            && metadata.getPlannedDistanceMeters()!=null && metadata.getVehicleId()!=null && metadata.getRouteId()!=null;
        var punctuality=punctuality(status,known,virtual,baseline);
        Double delay=known && (status==SimulationStatus.COMPLETED || status==SimulationStatus.RUNNING || status==SimulationStatus.PAUSED)
            ? Math.max(0,virtual-baseline):null;
        var key=new Key(tripId,attempt);
        return new SimulationReportItem(tripId,attempt,current,
            metadata==null?null:metadata.getVehicleId(),metadata==null?null:metadata.getVehiclePlateNumber(),
            metadata==null?null:metadata.getDriverId(),metadata==null?null:metadata.getDriverName(),
            metadata==null?null:metadata.getRouteName(),
            metadata==null || metadata.getAttemptStartedAt()==null?fallbackStart:metadata.getAttemptStartedAt(),
            ended,status,scenario,baseline,metadata==null?null:metadata.getPlannedDistanceMeters(),virtual,progress,
            delay,punctuality,known,events.getOrDefault(key,0L),List.copyOf(revisions.getOrDefault(key,List.of())));
    }
    private SimulationPunctuality punctuality(SimulationStatus status,boolean known,Double virtual,Double baseline) {
        if(!known) return SimulationPunctuality.UNKNOWN;
        if(status==SimulationStatus.COMPLETED) return virtual<=baseline+1e-7?SimulationPunctuality.ON_TIME:SimulationPunctuality.LATE;
        if(status==SimulationStatus.RUNNING || status==SimulationStatus.PAUSED)
            return virtual>baseline+1e-7?SimulationPunctuality.LATE:SimulationPunctuality.IN_PROGRESS;
        return SimulationPunctuality.NOT_COMPLETED;
    }
    private boolean matches(SimulationReportItem item,SimulationReportMetric metric) {
        return switch(metric) {
            case ALL -> true;
            case COMPLETED -> item.status()==SimulationStatus.COMPLETED;
            case ON_TIME -> item.punctuality()==SimulationPunctuality.ON_TIME;
            case LATE -> item.punctuality()==SimulationPunctuality.LATE;
            case OFF_ROUTE -> item.offRouteEventCount()>0;
        };
    }
}
