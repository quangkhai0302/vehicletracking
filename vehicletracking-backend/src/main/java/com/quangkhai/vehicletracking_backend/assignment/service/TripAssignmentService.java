package com.quangkhai.vehicletracking_backend.assignment.service;

import com.quangkhai.vehicletracking_backend.assignment.dto.*;
import com.quangkhai.vehicletracking_backend.assignment.entity.*;
import com.quangkhai.vehicletracking_backend.assignment.repository.TripAssignmentRequestRepository;
import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverDispatchInboxEntity;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverInboxKind;
import com.quangkhai.vehicletracking_backend.dispatch.repository.DriverDispatchInboxRepository;
import com.quangkhai.vehicletracking_backend.dispatch.service.DispatchAvailabilityService;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationSeverity;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.reroute.entity.TripNotificationEntity;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import com.quangkhai.vehicletracking_backend.vehicle.entity.VehicleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class TripAssignmentService {
    private final TripAssignmentRequestRepository requests;
    private final TripRepository trips;
    private final DriverRepository drivers;
    private final UserAccountRepository accounts;
    private final DriverDispatchInboxRepository inbox;
    private final TripNotificationRepository notifications;
    private final DispatchAvailabilityService availability;
    private final Clock operationsClock;

    @Transactional
    public TripAssignmentRequestEntity requestAssignment(TripEntity trip, long candidateId, long requestedByAccountId) {
        requireDirectTrip(trip);
        var candidate = drivers.findLockedById(candidateId)
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy tài xế."));
        requireCandidate(candidate, trip);
        var account = accounts.findById(requestedByAccountId)
                .filter(item -> item.isActive() && item.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> error(FORBIDDEN, "ASSIGNMENT_FORBIDDEN", "Cần tài khoản admin."));
        cancelPending(trip, "Admin thay đổi tài xế.");
        // Release the old PENDING row before inserting its replacement. PostgreSQL
        // partial unique indexes must not see both requests as PENDING in one flush.
        requests.flush();
        if (trip.getDriver() != null) {
            long oldId = trip.getDriver().getId();
            trip.assignDriver(null);
            if (oldId != candidateId) {
                sendInbox(oldId, trip.getId(), null, DriverInboxKind.TRIP_UNASSIGNED,
                        "Đã thu hồi phân công", "Bạn không còn được phân công chuyến này.",
                        unassignmentDedupeKey(trip.getId(), oldId));
            }
        }
        TripAssignmentRequestEntity request;
        try {
            request = requests.saveAndFlush(new TripAssignmentRequestEntity(trip, candidate, account, now()));
        } catch (DataIntegrityViolationException ex) {
            throw error(CONFLICT, "ASSIGNMENT_CANDIDATE_BUSY", "Tài xế vừa được gửi một yêu cầu nhận chuyến khác.");
        }
        sendInbox(candidateId, trip.getId(), request.getId(), DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED,
                "Yêu cầu nhận chuyến", "Vui lòng nhận hoặc từ chối yêu cầu nhận chuyến.",
                "assignment:" + request.getId() + ":requested");
        return request;
    }

    @Transactional(readOnly = true)
    public AssignmentRequestSummary latest(long tripId) {
        return requests.findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(tripId, TripAssignmentStatus.PENDING)
                .map(AssignmentRequestSummary::from)
                .orElseGet(() -> requests.findAllByTripIdOrderByRequestedAtDescIdDesc(tripId).stream().findFirst()
                        .map(AssignmentRequestSummary::from).orElse(null));
    }

    @Transactional(readOnly = true)
    public Map<Long, AssignmentRequestSummary> latestForTrips(List<Long> tripIds) {
        if (tripIds.isEmpty())
            return Map.of();
        Map<Long, AssignmentRequestSummary> latest = new LinkedHashMap<>();
        requests.findAllByTripIdInOrderByRequestedAtDescIdDesc(tripIds).forEach(
                request -> latest.putIfAbsent(request.getTrip().getId(), AssignmentRequestSummary.from(request)));
        requests.findAllByTripIdInAndStatusOrderByRequestedAtDescIdDesc(tripIds, TripAssignmentStatus.PENDING)
                .forEach(request -> latest.put(request.getTrip().getId(), AssignmentRequestSummary.from(request)));
        return latest;
    }

    @Transactional(readOnly = true)
    public List<DriverAssignmentRequestResponse> pendingForDriver(UserAccountPrincipal principal) {
        long driverId = driverId(principal);
        return requests.findAllByCandidateDriverIdAndStatusOrderByRequestedAtDescIdDesc(driverId,
                TripAssignmentStatus.PENDING, PageRequest.of(0, 50)).stream()
                .map(DriverAssignmentRequestResponse::from).toList();
    }

    @Transactional
    public AssignmentActionResponse accept(UserAccountPrincipal principal, UUID requestId) {
        long driverId = driverId(principal);
        var preview = ownRequest(requestId, driverId);
        TripEntity trip = trips.findLockedById(preview.getTrip().getId())
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu."));
        DriverEntity driver = drivers.findLockedById(driverId)
                .orElseThrow(() -> error(CONFLICT, "ASSIGNMENT_RESOURCE_CONFLICT", "Tài xế không còn khả dụng."));
        var request = requests.findLockedById(requestId)
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu."));
        if (!request.getCandidateDriver().getId().equals(driverId))
            throw notFound();
        if (request.getStatus() != TripAssignmentStatus.PENDING)
            throw error(CONFLICT, "ASSIGNMENT_REQUEST_NOT_PENDING", "Yêu cầu không còn chờ phản hồi.");
        requireDirectTrip(trip);
        VehicleEntity vehicle = trip.getVehicle();
        if (!vehicle.isActive() || !driver.isActive() || !accounts.existsActiveDriverAccount(driverId))
            throw error(CONFLICT, "ASSIGNMENT_RESOURCE_CONFLICT", "Xe hoặc tài xế không còn hoạt động.");
        if (availability.driverReservedForAuto(driverId, trip.getScheduledDepartureAt(),
                trip.getRoute().getEstimatedTripDurationSeconds(), trip.getId())
                || !availability.driverAvailable(driverId, trip, trip.getRoute().getEstimatedTripDurationSeconds(),
                        true)
                || !availability.vehicleAvailable(vehicle.getId(), trip,
                        trip.getRoute().getEstimatedTripDurationSeconds(), true))
            throw error(CONFLICT, "ASSIGNMENT_RESOURCE_CONFLICT", "Tài xế hoặc xe đã có lịch xung đột.");
        trip.assignDriver(driver);
        Instant now = now();
        request.accept(now);
        sendInbox(driverId, trip.getId(), requestId, DriverInboxKind.DIRECT_ASSIGNMENT_ACCEPTED,
                "Đã nhận chuyến", "Bạn đã nhận chuyến được điều phối.", "assignment:" + requestId + ":accepted");
        trips.flush();
        return AssignmentActionResponse.from(request);
    }

    @Transactional
    public AssignmentActionResponse decline(UserAccountPrincipal principal, UUID requestId, String reason) {
        long driverId = driverId(principal);
        var preview = ownRequest(requestId, driverId);
        TripEntity trip = trips.findLockedById(preview.getTrip().getId())
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu."));
        drivers.findLockedById(driverId)
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu."));
        var request = requests.findLockedById(requestId)
                .orElseThrow(() -> error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu."));
        if (!request.getCandidateDriver().getId().equals(driverId))
            throw notFound();
        if (request.getStatus() != TripAssignmentStatus.PENDING)
            throw error(CONFLICT, "ASSIGNMENT_REQUEST_NOT_PENDING", "Yêu cầu không còn chờ phản hồi.");
        requireDirectTrip(trip);
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500)
            throw error(BAD_REQUEST, "ASSIGNMENT_VALIDATION_FAILED", "Lý do từ chối phải dài từ 3 đến 500 ký tự.");
        Instant now = now();
        request.decline(now, normalized);
        notifications.save(new TripNotificationEntity(trip, null, NotificationType.DIRECT_ASSIGNMENT_DECLINED,
                NotificationSeverity.MAJOR, "Tài xế từ chối chuyến",
                normalized.substring(0, Math.min(255, normalized.length())),
                null, "", null, null, "assignment:" + requestId + ":declined", now));
        return AssignmentActionResponse.from(request);
    }

    @Transactional
    public void cancelPending(TripEntity trip, String reason) {
        requests.findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(trip.getId(), TripAssignmentStatus.PENDING)
                .ifPresent(request -> {
                    request.cancel(now(), normalizeReason(reason));
                    sendInbox(request.getCandidateDriver().getId(), trip.getId(), request.getId(),
                            DriverInboxKind.DIRECT_ASSIGNMENT_CANCELLED, "Yêu cầu đã hủy",
                            "Yêu cầu nhận chuyến không còn hiệu lực.", "assignment:" + request.getId() + ":cancelled");
                });
    }

    @Transactional
    public void unassign(TripEntity trip, String reason) {
        cancelPending(trip, reason);
        if (trip.getDriver() != null) {
            long oldDriverId = trip.getDriver().getId();
            trip.assignDriver(null);
            sendInbox(oldDriverId, trip.getId(), null, DriverInboxKind.TRIP_UNASSIGNED,
                    "Đã bỏ gán chuyến", "Bạn không còn được phân công chuyến này.",
                    unassignmentDedupeKey(trip.getId(), oldDriverId));
        }
    }

    public boolean hasHistory(long tripId) {
        return requests.existsByTripId(tripId);
    }

    @Transactional(readOnly = true)
    public boolean hasPending(long tripId) {
        return requests.findFirstByTripIdAndStatusOrderByRequestedAtDescIdDesc(tripId, TripAssignmentStatus.PENDING)
                .isPresent();
    }

    private void requireDirectTrip(TripEntity trip) {
        if (trip.getSchedule() != null || trip.getStatus() != TripStatus.SCHEDULED)
            throw error(CONFLICT, "ASSIGNMENT_INVALID_TRIP_STATE",
                    "Chỉ chuyến tức thời chưa khởi hành mới cần xác nhận.");
    }

    private void requireCandidate(DriverEntity driver, TripEntity trip) {
        if (!driver.isActive() || !accounts.existsActiveDriverAccount(driver.getId()))
            throw error(CONFLICT, "ASSIGNMENT_RESOURCE_CONFLICT", "Tài xế chưa có tài khoản hoạt động.");
        boolean busyByOtherTrip = requests.findAllByCandidateDriverIdAndStatusOrderByRequestedAtDescIdDesc(
                driver.getId(), TripAssignmentStatus.PENDING, PageRequest.of(0, 50)).stream()
                .anyMatch(item -> !item.getTrip().getId().equals(trip.getId()));
        if (busyByOtherTrip)
            throw error(CONFLICT, "ASSIGNMENT_CANDIDATE_BUSY", "Tài xế đang có yêu cầu nhận chuyến khác.");
        if (availability.driverReservedForAuto(driver.getId(), trip.getScheduledDepartureAt(),
                trip.getRoute().getEstimatedTripDurationSeconds(), trip.getId()))
            throw error(CONFLICT, "ASSIGNMENT_CANDIDATE_BUSY", "Tài xế đang được giữ chỗ cho chuyến tự động.");
        if (!availability.driverAvailable(driver.getId(), trip,
                trip.getRoute().getEstimatedTripDurationSeconds(), false))
            throw error(CONFLICT, "ASSIGNMENT_CANDIDATE_BUSY", "Tài xế đã có lịch chạy xung đột.");
    }

    private TripAssignmentRequestEntity ownRequest(UUID id, long driverId) {
        var request = requests.findById(id).orElseThrow(this::notFound);
        if (!request.getCandidateDriver().getId().equals(driverId))
            throw notFound();
        return request;
    }

    private long driverId(UserAccountPrincipal principal) {
        if (principal == null || principal.driverId() == null)
            throw error(FORBIDDEN, "ASSIGNMENT_FORBIDDEN", "Tài khoản chưa được gắn hồ sơ tài xế.");
        return principal.driverId();
    }

    private void sendInbox(long driverId, long tripId, UUID requestId, DriverInboxKind kind,
            String title, String detail, String dedupe) {
        if (!inbox.existsByDedupeKey(dedupe))
            inbox.save(new DriverDispatchInboxEntity(driverId, tripId, null, requestId, kind, title, detail, dedupe,
                    now()));
    }
    private String unassignmentDedupeKey(long tripId, long driverId) {
        return requests.findFirstByTripIdAndCandidateDriverIdAndStatusOrderByRespondedAtDescIdDesc(
                        tripId, driverId, TripAssignmentStatus.ACCEPTED)
                .map(request -> "direct:" + tripId + ":unassigned:" + driverId + ":" + request.getId())
                .orElse("direct:" + tripId + ":unassigned:" + driverId + ":legacy");
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "Hủy yêu cầu theo điều phối." : reason.trim();
        return normalized.length() < 3 ? "Hủy yêu cầu theo điều phối."
                : normalized.substring(0, Math.min(500, normalized.length()));
    }

    private Instant now() {
        return (operationsClock == null ? Instant.now() : operationsClock.instant()).truncatedTo(ChronoUnit.MICROS);
    }

    private ResponseStatusException notFound() {
        return error(NOT_FOUND, "ASSIGNMENT_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu.");
    }

    private ResponseStatusException error(org.springframework.http.HttpStatus status, String code, String detail) {
        var ex = new ResponseStatusException(status, detail);
        ex.getBody().setProperty("code", code);
        return ex;
    }
}
