package com.quangkhai.vehicletracking_backend.simulation.service;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.simulation.dto.SimulationIncidentResponse;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentEntity;
import com.quangkhai.vehicletracking_backend.simulation.entity.SimulationIncidentStatus;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationIncidentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SimulationIncidentService {
    private final SimulationIncidentRepository incidents;
    private final Clock operationsClock;

    @Transactional
    public SimulationIncidentResponse acknowledge(long id) {
        var incident = lock(id);
        if (incident.getStatus() == SimulationIncidentStatus.RESOLVED)
            throw new ResponseStatusException(CONFLICT, "Sự cố đã được xử lý.");
        incident.acknowledge(now());
        return SimulationIncidentResponse.from(incident, null);
    }

    private SimulationIncidentEntity lock(long id) {
        return incidents.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy sự cố."));
    }

    private java.time.Instant now() {
        return operationsClock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
