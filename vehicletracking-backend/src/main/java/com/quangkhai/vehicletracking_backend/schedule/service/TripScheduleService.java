package com.quangkhai.vehicletracking_backend.schedule.service;

import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
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
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class TripScheduleService {
    private final TripScheduleRepository schedules;
    private final RouteRepository routes;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
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
        TripScheduleEntity result = schedules.save(new TripScheduleEntity(values.name(), values.route(), values.vehicle(), values.driver(),
                input.frequency(), values.scheduledDate(), values.weekdaysMask(), input.departureTime(), values.timezone(),
                values.effectiveFrom(), values.effectiveUntil(), values.boardings()));
        return response(result, now());
    }
    @Transactional
    public ScheduleResponse update(long id, ScheduleUpsertRequest input) {
        TripScheduleEntity schedule = schedules.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lịch chạy."));
        ScheduleValues values = validate(input);
        schedule.update(values.name(), values.route(), values.vehicle(), values.driver(), input.frequency(), values.scheduledDate(),
                values.weekdaysMask(), input.departureTime(), values.timezone(), values.effectiveFrom(), values.effectiveUntil());
        schedule.updateExpectedEmployeeBoardings(values.boardings());
        return response(schedule, now());
    }
    @Transactional
    public ScheduleResponse enable(long id) {
        TripScheduleEntity schedule = locked(id);
        if (!schedule.isEnabled()) {
            schedule.enable();
        }
        return response(schedule, now());
    }
    @Transactional
    public ScheduleResponse disable(long id) {
        TripScheduleEntity schedule = locked(id);
        if (schedule.isEnabled()) {
            schedule.disable();
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
        Map<Integer, Integer> boardings = input.expectedEmployeeBoardings() == null ? Map.of() : Map.copyOf(input.expectedEmployeeBoardings());
        int finalSequence = route.getStops().stream().mapToInt(stop -> stop.getSequenceNumber()).max().orElseThrow();
        int cumulative = 0;
        for (var stop : route.getStops()) {
            Integer count = boardings.get(stop.getSequenceNumber());
            if (count != null && count < 0) throw new ResponseStatusException(BAD_REQUEST, "Số người dự kiến không được âm.");
            if (stop.getSequenceNumber() == finalSequence && count != null && count != 0)
                throw new ResponseStatusException(BAD_REQUEST, "Điểm đến cuối không nhận người lên xe.");
            if (stop.getSequenceNumber() != finalSequence) cumulative = Math.addExact(cumulative, count == null ? 0 : count);
        }
        if (vehicle.getSeatCapacity() != null && cumulative > vehicle.getSeatCapacity())
            throw new ResponseStatusException(CONFLICT, "Số người dự kiến vượt quá sức chứa của xe.");
        if (boardings.keySet().stream().anyMatch(sequence -> route.getStops().stream().noneMatch(stop -> stop.getSequenceNumber().equals(sequence))))
            throw new ResponseStatusException(BAD_REQUEST, "Cấu hình số người có trạm không thuộc tuyến.");
        String name = input.name() == null || input.name().isBlank() ? null : input.name().trim();
        return new ScheduleValues(name, route, vehicle, driver, input.scheduledDate(), mask, timezone,
                input.effectiveFrom(), input.effectiveUntil(), boardings);
    }
    private Instant now() { return operationsClock.instant(); }
    private record ScheduleValues(String name, RouteEntity route, VehicleEntity vehicle, DriverEntity driver,
            LocalDate scheduledDate, short weekdaysMask, String timezone, LocalDate effectiveFrom, LocalDate effectiveUntil,
            Map<Integer, Integer> boardings) {}
}
