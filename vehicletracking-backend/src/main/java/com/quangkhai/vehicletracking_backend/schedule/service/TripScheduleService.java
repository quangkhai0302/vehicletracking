package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.dispatch.service.DispatchLifecycleService;
import com.quangkhai.vehicletracking_backend.route.entity.RouteEntity;
import com.quangkhai.vehicletracking_backend.route.repository.RouteRepository;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleResponse;
import com.quangkhai.vehicletracking_backend.schedule.dto.ScheduleUpsertRequest;
import com.quangkhai.vehicletracking_backend.schedule.entity.ScheduleFrequency;
import com.quangkhai.vehicletracking_backend.schedule.entity.TripScheduleEntity;
import com.quangkhai.vehicletracking_backend.schedule.repository.TripScheduleRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import com.quangkhai.vehicletracking_backend.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashSet;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class TripScheduleService {
    private final TripScheduleRepository schedules;
    private final RouteRepository routes;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final UserAccountRepository accounts;
    private final DispatchLifecycleService dispatchLifecycle;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAll() {
        Instant now = now();
        return schedules.findAllByOrderByCreatedAtDescIdDesc().stream().map(item -> response(item, now)).toList();
    }
    @Transactional(readOnly = true)
    public ScheduleResponse findById(long id) { return response(find(id), now()); }
    @Transactional
    public ScheduleResponse create(ScheduleUpsertRequest input) {
        ScheduleValues values = validate(input);
        DispatchPolicy policy = policy(input, null, values.driver().getId());
        TripScheduleEntity result = schedules.save(new TripScheduleEntity(values.name(), values.route(), values.vehicle(), values.driver(),
                input.frequency(), values.scheduledDate(), values.weekdaysMask(), input.departureTime(), values.timezone(),
                values.effectiveFrom(), values.effectiveUntil()));
        result.setDispatchPolicy(policy.mode(), policy.backupEnabled(), policy.driverIds());
        return response(result, now());
    }
    @Transactional
    public ScheduleResponse update(long id, ScheduleUpsertRequest input) {
        TripScheduleEntity schedule = schedules.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lịch chạy."));
        ScheduleValues values = validate(input);
        DispatchPolicy policy = policy(input, schedule, values.driver().getId());
        schedule.update(values.name(), values.route(), values.vehicle(), values.driver(), input.frequency(), values.scheduledDate(),
                values.weekdaysMask(), input.departureTime(), values.timezone(), values.effectiveFrom(), values.effectiveUntil());
        if (policy != null) schedule.setDispatchPolicy(policy.mode(), policy.backupEnabled(), policy.driverIds());
        return response(schedule, now());
    }
    @Transactional
    public ScheduleResponse enable(long id) {
        TripScheduleEntity schedule = locked(id);
        if (!schedule.isEnabled()) {
            schedule.enable();
            dispatchLifecycle.scheduleChanged(id, true, schedule.getDispatchEpoch());
        }
        return response(schedule, now());
    }
    @Transactional
    public ScheduleResponse disable(long id) {
        TripScheduleEntity schedule = locked(id);
        if (schedule.isEnabled()) {
            schedule.disable();
            dispatchLifecycle.scheduleChanged(id, false, schedule.getDispatchEpoch());
        }
        return response(schedule, now());
    }

    private ScheduleResponse response(TripScheduleEntity schedule, Instant now) {
        Instant nextRunAt = null;
        try {
            nextRunAt = ScheduleOccurrenceResolver.next(schedule, now).orElse(null);
        } catch (ResponseStatusException ignored) {
            // Keep the schedule list usable when its next local occurrence falls in a DST gap.
            // The polling scheduler records the validation failure in lastRunStatus/message.
        }
        return ScheduleResponse.from(schedule, nextRunAt);
    }
    private TripScheduleEntity find(long id) {
        return schedules.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lịch chạy."));
    }
    private TripScheduleEntity locked(long id) {
        return schedules.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lịch chạy."));
    }
    private ScheduleValues validate(ScheduleUpsertRequest input) {
        String timezone = input.timezone().trim();
        ZoneId zone = ScheduleOccurrenceResolver.zone(timezone);
        if (zone instanceof ZoneOffset)
            throw new ResponseStatusException(BAD_REQUEST, "Múi giờ phải là tên vùng IANA, không phải offset cố định.");
        short mask = input.weekdaysMask() == null ? 0 : input.weekdaysMask();
        if (input.frequency() == ScheduleFrequency.ONCE) {
            if (input.scheduledDate() == null || mask != 0)
                throw new ResponseStatusException(BAD_REQUEST, "Lịch một lần cần ngày chạy và không chọn thứ trong tuần.");
        } else if (input.scheduledDate() != null || mask < 1 || mask > 127) {
            throw new ResponseStatusException(BAD_REQUEST, "Lịch hằng tuần cần ít nhất một thứ và không có ngày chạy riêng.");
        }
        if (input.effectiveUntil() != null && input.effectiveUntil().isBefore(input.effectiveFrom()))
            throw new ResponseStatusException(BAD_REQUEST, "Ngày kết thúc không được trước ngày bắt đầu.");
        if (input.frequency() == ScheduleFrequency.ONCE && (input.scheduledDate().isBefore(input.effectiveFrom())
                || (input.effectiveUntil() != null && input.scheduledDate().isAfter(input.effectiveUntil()))))
            throw new ResponseStatusException(BAD_REQUEST, "Ngày chạy phải nằm trong hiệu lực của lịch.");
        if (input.frequency() == ScheduleFrequency.ONCE)
            ScheduleOccurrenceResolver.resolve(input.scheduledDate(), input.departureTime(), zone);
        VehicleEntity vehicle = vehicles.findLockedById(input.vehicleId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy xe."));
        if (!vehicle.isActive()) throw new ResponseStatusException(CONFLICT, "Xe đã ngừng sử dụng.");
        DriverEntity driver = drivers.findLockedById(input.driverId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
        if (!driver.isActive()) throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng sử dụng.");
        // Keep the lock order aligned with TripService (vehicle -> driver -> route) to avoid deadlocks.
        RouteEntity route = routes.findLockedById(input.routeId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tuyến đường."));
        if (!route.isActive()) throw new ResponseStatusException(CONFLICT, "Tuyến đã ngừng sử dụng.");
        if (route.getStops().size() < 2 || route.getStops().stream().anyMatch(stop -> !stop.getStation().isActive()))
            throw new ResponseStatusException(CONFLICT, "Tuyến cần tối thiểu hai trạm đang hoạt động.");
        String name = input.name() == null || input.name().isBlank() ? null : input.name().trim();
        return new ScheduleValues(name, route, vehicle, driver, input.scheduledDate(), mask, timezone,
                input.effectiveFrom(), input.effectiveUntil());
    }
    private Instant now() { return operationsClock.instant(); }
    private DispatchPolicy policy(ScheduleUpsertRequest input, TripScheduleEntity current, long primaryDriverId) {
        boolean noFields = input.startMode() == null && input.backupEnabled() == null && input.backupDriverIds() == null;
        if (noFields) {
            if (current != null) {
                validateBackupPool(current.getStartMode(), current.isBackupEnabled(),
                        current.getBackupDriverIds(), primaryDriverId);
                return null;
            }
            return new DispatchPolicy(DispatchStartMode.MANUAL, false, List.of());
        }
        if (input.startMode() == null || input.backupEnabled() == null || input.backupDriverIds() == null)
            throw new ResponseStatusException(BAD_REQUEST, "Chính sách điều phối phải gồm đủ chế độ, bật dự phòng và danh sách tài xế.");
        validateBackupPool(input.startMode(), input.backupEnabled(), input.backupDriverIds(), primaryDriverId);
        return new DispatchPolicy(input.startMode(), input.backupEnabled(), List.copyOf(input.backupDriverIds()));
    }
    private void validateBackupPool(DispatchStartMode mode, boolean backupEnabled, List<Long> ids,
                                    long primaryDriverId) {
        if (ids.size() > 20 || (backupEnabled && ids.isEmpty())
                || (!backupEnabled && !ids.isEmpty())
                || (mode == DispatchStartMode.MANUAL && backupEnabled))
            throw new ResponseStatusException(BAD_REQUEST, "Danh sách tài xế dự phòng không hợp lệ với chế độ đã chọn.");
        if (new HashSet<>(ids).size() != ids.size() || ids.contains(primaryDriverId))
            throw new ResponseStatusException(BAD_REQUEST, "Tài xế dự phòng không được trùng nhau hoặc trùng tài xế chính.");
        for (Long id : ids) {
            if (id == null || id < 1) throw new ResponseStatusException(BAD_REQUEST, "ID tài xế dự phòng không hợp lệ.");
            DriverEntity candidate = drivers.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Không tìm thấy tài xế dự phòng."));
            if (!candidate.isActive() || !accounts.existsActiveDriverAccount(id))
                throw new ResponseStatusException(BAD_REQUEST, "Tài xế dự phòng phải hoạt động và có tài khoản DRIVER đang hoạt động.");
        }
    }
    private record DispatchPolicy(DispatchStartMode mode, boolean backupEnabled, List<Long> driverIds) {}
    private record ScheduleValues(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            LocalDate scheduledDate, short weekdaysMask, String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil) {}
}
