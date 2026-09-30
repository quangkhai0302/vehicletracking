package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.dispatch.dto.*;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.*;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.simulation.repository.SimulationRepository;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.telemetry.entity.TelemetrySource;
import com.quangkhai.vehicletracking_backend.telemetry.repository.TelemetryRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class AdminTripDispatchService {
    private final TripRepository trips;
    private final TripDispatchRepository dispatches;
    private final TripDispatchOfferRepository offers;
    private final TripDispatchEventRepository events;
    private final DriverRepository drivers;
    private final UserAccountRepository accounts;
    private final SimulationRepository runs;
    private final TelemetryRepository samples;
    private final DispatchAvailabilityService availability;
    private final DriverDispatchService commands;
    private final SimulationService simulation;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public DispatchDetail detail(long tripId) {
        TripEntity trip = trips.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến."));
        return projection(trip, dispatches.findById(tripId).orElse(null));
    }

    @Transactional
    public DispatchDetail policy(UserAccountPrincipal principal, long tripId, DispatchPolicyRequest input) {
        long accountId = adminAccountId(principal);
        TripEntity trip = lockedTrip(tripId);
        if (trip.getSchedule() == null || trip.getStatus() != TripStatus.SCHEDULED
                || runs.existsByTripId(tripId) || samples.existsByTripIdAndSource(tripId, TelemetrySource.GPS))
            throw conflict("DISPATCH_INVALID_STATE");
        TripDispatchEntity dispatch = dispatches.findById(tripId).orElse(null);
        if (dispatch == null ? input.expectedRevision() != null
                : input.expectedRevision() == null || dispatch.getRevision() != input.expectedRevision())
            throw conflict("DISPATCH_STALE_REVISION");
        if (input.startMode() == DispatchStartMode.AUTO_IF_READY
                && (!trip.getSchedule().isEnabled()
                    || !operationsClock.instant().isBefore(trip.getScheduledDepartureAt().plusSeconds(15 * 60))))
            throw conflict("DISPATCH_WINDOW_CLOSED");
        validatePolicy(input, trip.getSchedule().getDriver().getId());
        Instant now = operationsClock.instant();
        if (dispatch == null) {
            long duration = Math.max(1, trip.getStops().stream()
                    .mapToLong(stop -> stop.getDepartureOffsetSeconds()).max().orElse(1));
            dispatch = new TripDispatchEntity(trip, trip.getSchedule(), duration, now);
            dispatches.save(dispatch);
            commands.event(dispatch, DispatchEventKind.CREATED, DispatchActorKind.SYSTEM,
                    null, null, null, trip.getDriver() == null ? null : trip.getDriver().getId(), null, now);
        } else {
            offers.findByDispatchTripIdAndStatus(tripId, DispatchOfferStatus.PENDING)
                    .ifPresent(offer -> offer.resolve(DispatchOfferStatus.CANCELLED, now, "Chính sách đã thay đổi."));
        }
        dispatch.setPolicy(input.startMode(), input.backupEnabled(), input.backupDriverIds(),
                trip.getSchedule().getDispatchEpoch(), now);
        commands.event(dispatch, DispatchEventKind.POLICY_CHANGED, DispatchActorKind.ADMIN,
                accountId, null, null, trip.getDriver() == null ? null : trip.getDriver().getId(),
                "Đổi chính sách điều phối chuyến.", now);
        return projection(trip, dispatch);
    }

    @Transactional
    public DispatchOverrideResponse overrideStart(UserAccountPrincipal principal, long tripId,
                                                   DispatchOverrideRequest input) {
        long accountId = adminAccountId(principal);
        TripEntity trip = lockedTrip(tripId);
        TripDispatchEntity dispatch = dispatches.findById(tripId)
                .orElseThrow(() -> conflict("DISPATCH_INVALID_STATE"));
        commands.checkRevision(dispatch, input.expectedRevision());
        commands.requireActiveSchedule(dispatch);
        if (dispatch.getStartMode() != DispatchStartMode.AUTO_IF_READY
                || trip.getStatus() != TripStatus.SCHEDULED
                || trip.getDriver() == null || trip.getAttemptNumber() != 1
                || operationsClock.instant().isBefore(trip.getScheduledDepartureAt())
                || runs.existsByTripId(tripId)
                || samples.existsByTripIdAndSource(tripId, TelemetrySource.GPS))
            throw conflict("DISPATCH_INVALID_STATE");
        if (!availability.driverAvailable(trip.getDriver().getId(), trip, dispatch.getBaselineDurationSeconds(), false)
                || !availability.vehicleAvailable(trip.getVehicle().getId(), trip, dispatch.getBaselineDurationSeconds()))
            throw conflict("RESOURCE_CONFLICT");
        if (input.reason() == null || input.reason().trim().length() < 10
                || input.reason().trim().length() > 500)
            throw DispatchProblemException.invalid("Lý do khởi hành ngoại lệ phải dài từ 10 đến 500 ký tự.");
        Instant now = operationsClock.instant();
        var result = simulation.playAuto(tripId);
        dispatch.started(now);
        commands.event(dispatch, DispatchEventKind.OVERRIDE_STARTED, DispatchActorKind.ADMIN,
                accountId, null, null, trip.getDriver().getId(), input.reason().trim(), now);
        commands.sendInbox(trip.getDriver().getId(), tripId, null, DriverInboxKind.TRIP_STARTED,
                "Chuyến đã khởi hành", "Admin đã khởi hành chuyến theo ngoại lệ.",
                "trip:" + tripId + ":override-start", now);
        commands.notifyAdmin(dispatch, NotificationType.TRIP_AUTO_STARTED, "Khởi hành ngoại lệ",
                "Admin đã khởi hành mô phỏng sau khi kiểm tra nguồn lực.", now);
        return new DispatchOverrideResponse(projection(trip, dispatch), result);
    }

    private DispatchDetail projection(TripEntity trip, TripDispatchEntity dispatch) {
        if (dispatch == null) return new DispatchDetail(trip.getId(), DispatchSummary.legacyManual(),
                trip.getSchedule() == null ? null : trip.getSchedule().getDriver().getId(),
                trip.getDriver() == null ? null : trip.getDriver().getId(), List.of(), null, List.of());
        List<DispatchDetail.Candidate> candidates = java.util.stream.IntStream.range(0, dispatch.getBackupDriverIds().size())
                .mapToObj(index -> {
                    long id = dispatch.getBackupDriverIds().get(index);
                    String name = drivers.findById(id).map(item -> item.getFullName()).orElse("Tài xế không còn trong danh mục");
                    return new DispatchDetail.Candidate(id, name, index + 1);
                }).toList();
        DispatchDetail.Offer active = offers.findByDispatchTripIdAndStatus(trip.getId(), DispatchOfferStatus.PENDING)
                .map(item -> new DispatchDetail.Offer(item.getId(), item.getCandidateDriverId(), item.getExpiresAt()))
                .orElse(null);
        List<DispatchDetail.Event> history = events.findTop100ByDispatchTripIdOrderByCreatedAtDescIdDesc(trip.getId())
                .stream().map(item -> new DispatchDetail.Event(item.getRevision(), item.getKind(), item.getActorKind(),
                        item.getFromDriverId(), item.getToDriverId(), item.getReason(), item.getCreatedAt())).toList();
        return new DispatchDetail(trip.getId(), DispatchSummary.from(dispatch), dispatch.getPrimaryDriver().getId(),
                trip.getDriver() == null ? null : trip.getDriver().getId(), candidates, active, history);
    }
    private void validatePolicy(DispatchPolicyRequest input, long primaryDriverId) {
        List<Long> ids = input.backupDriverIds();
        if (ids == null || ids.size() > 20 || (input.backupEnabled() && ids.isEmpty())
                || (!input.backupEnabled() && !ids.isEmpty())
                || (input.startMode() == DispatchStartMode.MANUAL && input.backupEnabled())
                || new HashSet<>(ids).size() != ids.size() || ids.contains(primaryDriverId))
            throw DispatchProblemException.invalid("Danh sách tài xế dự phòng không hợp lệ.");
        for (Long id : ids) {
            if (id == null || id < 1 || drivers.findById(id).filter(item -> item.isActive()).isEmpty()
                    || !accounts.existsActiveDriverAccount(id))
                throw DispatchProblemException.invalid("Tài xế dự phòng phải hoạt động và có tài khoản DRIVER.");
        }
    }
    private TripEntity lockedTrip(long id) {
        return trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến."));
    }
    private long adminAccountId(UserAccountPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(FORBIDDEN, "Cần tài khoản admin.");
        return principal.accountId();
    }
    private ResponseStatusException conflict(String code) { return DispatchProblemException.conflict(code); }
}
