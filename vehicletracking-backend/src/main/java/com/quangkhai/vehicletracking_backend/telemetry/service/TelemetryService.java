package com.quangkhai.vehicletracking_backend.telemetry.service;

import com.quangkhai.vehicletracking_backend.telemetry.dto.*;
import com.quangkhai.vehicletracking_backend.telemetry.entity.*;
import com.quangkhai.vehicletracking_backend.telemetry.repository.*;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static org.springframework.http.HttpStatus.*;

@Service @RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository samples;
    private final VehiclePositionRepository positions;
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final SimulationRepository simulations;
    private final Clock operationsClock;

    @Transactional
    public TelemetryResponse ingestGps(TelemetryRequest input) {
        if (input.source()!=TelemetrySource.GPS) throw new ResponseStatusException(BAD_REQUEST,"Nguồn SIMULATOR chỉ do backend phát.");
        return ingest(input,null);
    }
    @Transactional
    public TelemetryResponse ingestSimulator(TelemetryRequest input, Instant simulatedAt) {
        if (input.source()!=TelemetrySource.SIMULATOR || simulatedAt==null) throw new IllegalArgumentException("Missing simulator provenance");
        return ingest(input,simulatedAt);
    }
    private TelemetryResponse ingest(TelemetryRequest raw, Instant simulatedAt) {
        validate(raw);
        var input=new TelemetryRequest(raw.eventId(),raw.vehicleId(),raw.tripId(),raw.recordedAt().truncatedTo(ChronoUnit.MICROS),
            raw.latitude(),raw.longitude(),raw.speedKmh(),raw.heading(),raw.accuracyMeters(),raw.source());
        var duplicate=samples.findByEventId(input.eventId());
        if (duplicate.isPresent()) return duplicate(duplicate.get(),input);
        var trip=trips.findLockedById(input.tripId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Không tìm thấy chuyến."));
        var vehicle=vehicles.findLockedById(trip.getVehicle().getId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Không tìm thấy xe."));
        duplicate=samples.findByEventId(input.eventId());
        if (duplicate.isPresent()) return duplicate(duplicate.get(),input);
        if (!vehicle.getId().equals(input.vehicleId()) || !vehicle.isActive() || trip.getStatus()!=TripStatus.IN_PROGRESS)
            throw new ResponseStatusException(CONFLICT,"Bản tin phải thuộc đúng xe đang sử dụng và chuyến đang thực hiện.");
        if (input.source()==TelemetrySource.GPS && simulations.existsByTripId(trip.getId()))
            throw new ResponseStatusException(CONFLICT,"Chuyến này thuộc simulator; không nhận lẫn dữ liệu GPS.");
        var latest=positions.findById(vehicle.getId());
        if (latest.isPresent() && !input.recordedAt().isAfter(latest.get().getSample().getRecordedAt()))
            throw new ResponseStatusException(CONFLICT,"Bản tin cũ hoặc trùng thời điểm; vị trí mới nhất được giữ nguyên.");
        var sample=new TelemetrySampleEntity(input,operationsClock.instant().truncatedTo(ChronoUnit.MICROS),
            simulatedAt==null?null:simulatedAt.truncatedTo(ChronoUnit.MICROS));
        try { samples.saveAndFlush(sample); }
        catch(DataIntegrityViolationException ex) { throw new ResponseStatusException(CONFLICT,"eventId đã được dùng bởi bản tin khác.",ex); }
        if (latest.isPresent()) latest.get().update(sample);
        else positions.save(new VehiclePositionEntity(vehicle.getId(),sample));
        return TelemetryResponse.from(sample);
    }
    private TelemetryResponse duplicate(TelemetrySampleEntity sample, TelemetryRequest input) {
        if (!sample.matches(input)) throw new ResponseStatusException(CONFLICT,"eventId đã được dùng với nội dung khác.");
        return TelemetryResponse.from(sample);
    }
    private void validate(TelemetryRequest input) {
        if (input.eventId()==null || input.vehicleId()==null || input.vehicleId()<=0 || input.tripId()==null || input.tripId()<=0
                || input.source()==null || input.recordedAt()==null || input.recordedAt().isBefore(Instant.parse("2000-01-01T00:00:00Z"))
                || input.recordedAt().isAfter(operationsClock.instant().plusSeconds(30))
                || !range(input.latitude(),-90,90) || !range(input.longitude(),-180,180)
                || !range(input.speedKmh(),0,500) || !range(input.heading(),0,360) || input.heading()==360
                || !range(input.accuracyMeters(),0,10000))
            throw new ResponseStatusException(BAD_REQUEST,"Bản tin vị trí không hợp lệ; kiểm tra tọa độ, đơn vị và thời điểm đo.");
    }
    private boolean range(Double value,double min,double max) { return value!=null && Double.isFinite(value) && value>=min && value<=max; }
}
