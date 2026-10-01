package com.quangkhai.vehicletracking_backend.driver.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.quangkhai.vehicletracking_backend.driver.dto.DriverResponse;
import com.quangkhai.vehicletracking_backend.driver.dto.DriverUpsertRequest;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverService {
    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    private static final int MAX_AVATAR_DIMENSION = 2_000;

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

    @Transactional
    public void updateAvatar(long id, MultipartFile file) {
        DriverEntity driver = findLocked(id);
        if (!driver.isActive()) {
            throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng sử dụng.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Vui lòng chọn ảnh đại diện.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Ảnh đại diện không được vượt quá 2 MB.");
        }

        String contentType = file.getContentType();
        if (!MediaType.IMAGE_JPEG_VALUE.equals(contentType) && !MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
            throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ ảnh PNG hoặc JPEG.");
        }

        byte[] data;
        try {
            data = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Tệp tải lên không phải là ảnh hợp lệ.");
            }
            if (image.getWidth() > MAX_AVATAR_DIMENSION || image.getHeight() > MAX_AVATAR_DIMENSION) {
                throw new ResponseStatusException(BAD_REQUEST, "Ảnh đại diện không được vượt quá 2000x2000 pixel.");
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Không thể đọc ảnh đại diện.", ex);
        }

        driver.updateAvatar(data, contentType);
        drivers.saveAndFlush(driver);
    }

    @Transactional
    public void clearAvatar(long id) {
        DriverEntity driver = findLocked(id);
        driver.clearAvatar();
        drivers.saveAndFlush(driver);
    }

    @Transactional(readOnly = true)
    public AvatarFile avatar(long id) {
        DriverEntity driver = drivers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
        if (!driver.hasAvatar()) {
            throw new ResponseStatusException(NOT_FOUND, "Tài xế chưa có ảnh đại diện.");
        }
        return new AvatarFile(driver.avatarDataCopy(), driver.avatarContentType());
    }

    public record AvatarFile(byte[] data, String contentType) { }

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
