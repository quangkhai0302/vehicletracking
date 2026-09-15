package com.quangkhai.vehicletracking_backend.vehicle.service;

import com.quangkhai.vehicletracking_backend.vehicle.dto.*;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private final VehicleRepository vehicles;
    private final TripRepository trips;

    @Transactional(readOnly = true)
    public List<VehicleResponse> findAll() {
        return vehicles.findAllByOrderByPlateNumberAsc().stream().map(VehicleResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public VehicleResponse findById(long id) {
        return VehicleResponse.from(vehicles.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy xe.")));
    }
    @Transactional
    public VehicleResponse create(VehicleUpsertRequest input) {
        String plate = normalizePlate(input.plateNumber());
        ensureUnique(plate, -1L);
        return persist(new VehicleEntity(plate, input.name().trim(), normalizeDescription(input.description()), input.vehicleType()));
    }
    @Transactional
    public VehicleResponse update(long id, VehicleUpsertRequest input) {
        VehicleEntity vehicle = findLocked(id);
        if (!vehicle.isActive()) throw new ResponseStatusException(CONFLICT, "Xe đã ngừng sử dụng.");
        String plate = normalizePlate(input.plateNumber());
        ensureUnique(plate, id);
        vehicle.updateDetails(plate, input.name().trim(), normalizeDescription(input.description()), input.vehicleType());
        return persist(vehicle);
    }
    @Transactional
    public void deactivate(long id) {
        VehicleEntity vehicle = findLocked(id);
        if (!vehicle.isActive()) return;
        if (trips.existsByVehicleIdAndStatusIn(id, List.of(TripStatus.SCHEDULED, TripStatus.IN_PROGRESS)))
            throw new ResponseStatusException(CONFLICT, "Hãy hoàn thành hoặc hủy các chuyến chưa kết thúc trước khi ngừng sử dụng xe.");
        vehicle.deactivate();
    }
    private VehicleEntity findLocked(long id) {
        return vehicles.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy xe."));
    }
    private VehicleResponse persist(VehicleEntity vehicle) {
        try { return VehicleResponse.from(vehicles.saveAndFlush(vehicle)); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Biển số đã tồn tại. Vui lòng kiểm tra lại.", ex);
        }
    }
    private void ensureUnique(String plate, Long exceptId) {
        if (vehicles.existsByPlateNumberAndIdNot(plate, exceptId))
            throw new ResponseStatusException(CONFLICT, "Biển số đã tồn tại, kể cả xe đã ngừng sử dụng.");
    }
    private String normalizePlate(String value) {
        String plate = value.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
        if (!plate.matches("[A-Z0-9]{1,20}")) throw new ResponseStatusException(BAD_REQUEST, "Biển số không hợp lệ.");
        return plate;
    }
    private String normalizeDescription(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
