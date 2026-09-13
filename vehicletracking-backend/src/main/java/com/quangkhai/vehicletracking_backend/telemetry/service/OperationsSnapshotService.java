package com.quangkhai.vehicletracking_backend.telemetry.service;
import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.VehiclePositionRepository;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.dto.TripSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class OperationsSnapshotService {
    private final VehiclePositionRepository positions;
    private final SimulationRepository runs;
    private final TripRepository trips;
    private final SimulationService simulation;
    private final Clock operationsClock;
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public OperationsSnapshot snapshot() {
        var tripList=trips.findAllByOrderByScheduledDepartureAtDescIdDesc();
        var byId=tripList.stream().collect(Collectors.toMap(t->t.getId(),t->t));
        var locationList=positions.findAllByOrderByVehicleIdAsc().stream().map(p->TelemetryResponse.from(p.getSample())).toList();
        var simulations=runs.findAllByOrderByIdAsc().stream().map(run->simulation.describe(byId.get(run.getTripId()),run)).toList();
        return new OperationsSnapshot(operationsClock.instant(),locationList,simulations,tripList.stream().map(TripSummaryResponse::from).toList());
    }
}
