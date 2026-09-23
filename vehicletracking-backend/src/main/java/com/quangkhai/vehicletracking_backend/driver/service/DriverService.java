package com.quangkhai.vehicletracking_backend.driver.service;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverResponse;
import com.quangkhai.vehicletracking_backend.driver.dto.DriverUpsertRequest;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
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
public class DriverService {
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final TripRepository trips;

    @Transactional(readOnly = true)
    public List<DriverResponse> findAll() {
        return drivers.findAllByOrderByLicenseNumberAsc().stream().map(DriverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse findById(long id) {
        return DriverResponse.from(drivers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế.")));
    }

    @Transactional
    public DriverResponse create(DriverUpsertRequest input) {
        String license = normalizeLicense(input.licenseNumber());
        ensureUnique(license, -1L);
        return persist(new DriverEntity(input.fullName().trim(), input.phoneNumber().trim(), license));
    }

    @Transactional
    public DriverResponse update(long id, DriverUpsertRequest input) {
        DriverEntity driver = findLocked(id);
        if (!driver.isActive()) throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng sử dụng.");
        String license = normalizeLicense(input.licenseNumber());
        ensureUnique(license, id);
        driver.updateDetails(input.fullName().trim(), input.phoneNumber().trim(), license);
        return persist(driver);
    }

    @Transactional
    public void deactivate(long id) {
        DriverEntity driver = findLocked(id);
        if (!driver.isActive()) return;
        if (vehicles.existsByDriverIdAndActiveTrue(id))
            throw new ResponseStatusException(CONFLICT, "Hãy bỏ gán tài xế khỏi xe trước khi ngừng sử dụng.");
        if (trips.existsByDriverIdAndStatusIn(id, List.of(TripStatus.SCHEDULED, TripStatus.IN_PROGRESS)))
            throw new ResponseStatusException(CONFLICT, "Hãy bỏ gán, hoàn thành hoặc hủy các chuyến chưa kết thúc trước khi ngừng tài xế.");
        driver.deactivate();
    }

    private DriverEntity findLocked(long id) {
        return drivers.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
    }

    private DriverResponse persist(DriverEntity driver) {
        try {
            return DriverResponse.from(drivers.saveAndFlush(driver));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Số GPLX đã tồn tại. Vui lòng kiểm tra lại.", ex);
        }
    }

    private void ensureUnique(String license, long exceptId) {
        if (drivers.existsByLicenseNumberAndIdNot(license, exceptId))
            throw new ResponseStatusException(CONFLICT, "Số GPLX đã tồn tại, kể cả tài xế đã ngừng sử dụng.");
    }

    private String normalizeLicense(String value) {
        String license = value.trim().toUpperCase(Locale.ROOT);
        if (!license.matches("[A-Z0-9.\\-]{1,50}"))
            throw new ResponseStatusException(BAD_REQUEST, "Số GPLX không hợp lệ.");
        return license;
    }
}
