package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchOfferRepository;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DispatchLifecycleService {
    private final TripDispatchRepository dispatches;
    private final TripRepository trips;
    private final TripDispatchOfferRepository offers;
    private final DriverDispatchService commands;
    private final Clock operationsClock;

    public void assignmentChanged(TripEntity trip, Long oldDriverId, String reason) {
        TripDispatchEntity dispatch = dispatches.findById(trip.getId()).orElse(null);
        if (dispatch == null || dispatch.getStartMode() != DispatchStartMode.AUTO_IF_READY) return;
        Instant now = operationsClock.instant();
        cancelOffer(trip, now);
        dispatch.assignmentChanged(now, trip.getDriver() == null && dispatch.isBackupEnabled());
        var principal = adminPrincipal();
        commands.event(dispatch, DispatchEventKind.REASSIGNED,
                principal == null ? DispatchActorKind.SYSTEM : DispatchActorKind.ADMIN,
                principal == null ? null : principal.accountId(), null, oldDriverId,
                trip.getDriver() == null ? null : trip.getDriver().getId(), reason, now);
        if (dispatch.getAttentionCode() == DispatchAttentionCode.NO_BACKUP)
            commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION,
                    "Chuyến chưa có tài xế", "Admin cần phân công tài xế cho chuyến.", now);
        if (oldDriverId != null && (trip.getDriver() == null || !oldDriverId.equals(trip.getDriver().getId())))
            commands.sendInbox(oldDriverId, trip.getId(), null, DriverInboxKind.TRIP_UNASSIGNED,
                    "Phân công đã thay đổi", "Bạn không còn được phân công chuyến này.",
                    "trip:" + trip.getId() + ":assignment:" + dispatch.getRevision() + ":old", now);
        if (trip.getDriver() != null)
            commands.sendInbox(trip.getDriver().getId(), trip.getId(), null, DriverInboxKind.TRIP_ASSIGNED,
                    "Được phân công chuyến", "Vui lòng xác nhận sẵn sàng trong cửa sổ cho phép.",
                    "trip:" + trip.getId() + ":assignment:" + dispatch.getRevision() + ":new", now);
    }

    /** The schedule is locked by the caller. Update every unstarted dispatch in the same transaction. */
    public void scheduleChanged(long scheduleId, boolean enabled, long epoch) {
        for (Long tripId : dispatches.findTripIdsByScheduleId(scheduleId)) {
            TripEntity trip = trips.findLockedById(tripId).orElse(null);
            if (trip == null) continue;
            TripDispatchEntity dispatch = dispatches.findById(tripId).orElse(null);
            if (dispatch == null || dispatch.getStartMode() != DispatchStartMode.AUTO_IF_READY
                    || dispatch.getState() == DispatchState.STARTED || dispatch.getState() == DispatchState.CLOSED) continue;
            Instant now = operationsClock.instant();
            cancelOffer(trip, now);
            if (enabled) {
                dispatch.resumeForSchedule(epoch, now);
                commands.event(dispatch, DispatchEventKind.SCHEDULE_RESUMED, DispatchActorKind.SYSTEM,
                        null, null, null, null, null, now);
                if (dispatch.getAttentionCode() == DispatchAttentionCode.NO_BACKUP)
                    commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION,
                            "Chuyến chưa có tài xế", "Admin cần phân công tài xế sau khi bật lại lịch.", now);
            } else {
                dispatch.pauseForSchedule(now);
                commands.event(dispatch, DispatchEventKind.SCHEDULE_PAUSED, DispatchActorKind.SYSTEM,
                        null, null, null, null, null, now);
                commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION,
                        "Lịch đã tạm dừng", "Chuyến không tự khởi hành vì lịch đã tạm dừng.", now);
            }
        }
    }

    public void closed(TripEntity trip) {
        TripDispatchEntity dispatch = dispatches.findById(trip.getId()).orElse(null);
        if (dispatch == null || dispatch.getState() == DispatchState.CLOSED) return;
        Instant now = operationsClock.instant();
        cancelOffer(trip, now);
        dispatch.closed(now);
        var principal = adminPrincipal();
        commands.event(dispatch, DispatchEventKind.CLOSED,
                principal == null ? DispatchActorKind.SYSTEM : DispatchActorKind.ADMIN,
                principal == null ? null : principal.accountId(), null, null, null, null, now);
    }

    private void cancelOffer(TripEntity trip, Instant now) {
        offers.findByDispatchTripIdAndStatus(trip.getId(), DispatchOfferStatus.PENDING).ifPresent(offer -> {
            offer.resolve(DispatchOfferStatus.CANCELLED, now, "Phân công hoặc trạng thái chuyến đã đổi.");
            commands.sendInbox(offer.getCandidateDriverId(), trip.getId(), offer.getId(),
                    DriverInboxKind.OFFER_CANCELLED, "Lời mời đã hủy", "Lời mời không còn hiệu lực.",
                    "offer:" + offer.getId() + ":cancelled", now);
        });
    }
    private UserAccountPrincipal adminPrincipal() {
        Object value = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return value instanceof UserAccountPrincipal principal ? principal : null;
    }
}
