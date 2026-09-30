package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.*;
import com.quangkhai.vehicletracking_backend.reroute.entity.NotificationType;
import com.quangkhai.vehicletracking_backend.simulation.service.SimulationService;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TripDispatchJobService {
    private final TripRepository trips;
    private final TripDispatchRepository dispatches;
    private final TripDispatchOfferRepository offers;
    private final DriverDispatchService commands;
    private final DispatchAvailabilityService availability;
    private final SimulationService simulation;
    private final Clock operationsClock;

    @Transactional
    public void process(long tripId) {
        TripEntity trip = trips.findLockedById(tripId).orElse(null);
        if (trip == null) return;
        TripDispatchEntity dispatch = dispatches.findById(tripId).orElse(null);
        if (dispatch == null || dispatch.getStartMode() != DispatchStartMode.AUTO_IF_READY) return;
        Instant now = operationsClock.instant();
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            if (dispatch.getState() != DispatchState.CLOSED && dispatch.getState() != DispatchState.STARTED) {
                dispatch.closed(now);
                commands.event(dispatch, DispatchEventKind.CLOSED, DispatchActorKind.SYSTEM,
                        null, null, null, null, null, now);
            }
            return;
        }
        var schedule = trip.getSchedule();
        if (schedule == null || !schedule.isEnabled()) {
            cancelPending(trip, now);
            if (dispatch.getAttentionCode() != DispatchAttentionCode.SCHEDULE_DISABLED) {
                dispatch.pauseForSchedule(now);
                commands.event(dispatch, DispatchEventKind.SCHEDULE_PAUSED, DispatchActorKind.SYSTEM,
                        null, null, null, null, null, now);
                commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Lịch đã tạm dừng",
                        "Chuyến không tự khởi hành vì lịch đã tạm dừng.", now);
            }
            return;
        }
        if (dispatch.getScheduleEpoch() != schedule.getDispatchEpoch()) {
            cancelPending(trip, now);
            dispatch.resumeForSchedule(schedule.getDispatchEpoch(), now);
            commands.event(dispatch, DispatchEventKind.SCHEDULE_RESUMED, DispatchActorKind.SYSTEM,
                    null, null, null, null, null, now);
        }
        Instant departure = trip.getScheduledDepartureAt();
        Instant cutoff = departure.plusSeconds(15 * 60);
        if (!now.isBefore(cutoff)) {
            cancelPending(trip, now);
            if (dispatch.getAttentionCode() != DispatchAttentionCode.WINDOW_EXPIRED) {
                dispatch.attention(DispatchAttentionCode.WINDOW_EXPIRED, now);
                commands.event(dispatch, DispatchEventKind.ATTENTION_CHANGED, DispatchActorKind.SYSTEM,
                        null, null, null, null, "WINDOW_EXPIRED", now);
                commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Đã hết giờ tự khởi hành",
                        "Cần admin kiểm tra và xử lý chuyến.", now);
            }
            return;
        }
        var pending = offers.findByDispatchTripIdAndStatus(tripId, DispatchOfferStatus.PENDING).orElse(null);
        if (pending != null && !now.isBefore(pending.getExpiresAt())) {
            commands.expirePending(trip, dispatch, pending, now);
            offers.flush();
            pending = null;
        }
        if (dispatch.getState() == DispatchState.SEARCH_WAIT) {
            if (!now.isBefore(departure.minusSeconds(30 * 60)))
                commands.openNextOffer(trip, dispatch, now);
            return;
        }
        if (dispatch.getState() == DispatchState.OFFER_PENDING && pending != null) {
            if (!now.isBefore(departure) && dispatch.getDueAlertedAt() == null) {
                dispatch.dueAlerted(now);
                commands.event(dispatch, DispatchEventKind.DUE_ALERTED, DispatchActorKind.SYSTEM,
                        null, null, null, null, "OFFER_PENDING", now);
                commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Chuyến chưa thể khởi hành",
                        "Đang chờ tài xế dự phòng nhận lời mời.", now);
            }
            dispatch.waitForOfferExpiry(pending.getExpiresAt());
            return;
        }
        if (dispatch.getState() == DispatchState.WAITING_READY) {
            if (now.isBefore(departure)) {
                dispatch.waitUntilDeparture();
            } else {
                dispatch.attention(DispatchAttentionCode.DRIVER_NOT_READY, now);
                commands.event(dispatch, DispatchEventKind.DUE_ALERTED, DispatchActorKind.SYSTEM,
                        null, null, null, null, "DRIVER_NOT_READY", now);
                commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Tài xế chưa sẵn sàng",
                        "Chuyến vẫn chưa khởi hành; tài xế có thể xác nhận muộn trước hạn.", now);
            }
            return;
        }
        if (dispatch.getState() != DispatchState.READY || now.isBefore(departure)) return;
        if (!dispatch.isReadyFor(trip) || trip.getAttemptNumber() != 1 || trip.getDriver() == null
                || !availability.driverAvailable(trip.getDriver().getId(), trip, dispatch.getBaselineDurationSeconds(), false)
                || !availability.vehicleAvailable(trip.getVehicle().getId(), trip, dispatch.getBaselineDurationSeconds())) {
            dispatch.attention(DispatchAttentionCode.RESOURCE_UNAVAILABLE, now);
            commands.event(dispatch, DispatchEventKind.ATTENTION_CHANGED, DispatchActorKind.SYSTEM,
                    null, null, null, null, "RESOURCE_UNAVAILABLE", now);
            commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Nguồn lực không khả dụng",
                    "Cần kiểm tra tài xế, xe hoặc lần chạy của chuyến.", now);
            return;
        }
        simulation.playAuto(tripId);
        dispatch.started(now);
        commands.event(dispatch, DispatchEventKind.AUTO_STARTED, DispatchActorKind.SYSTEM,
                null, null, null, trip.getDriver().getId(), null, now);
        commands.sendInbox(trip.getDriver().getId(), tripId, null, DriverInboxKind.TRIP_STARTED,
                "Chuyến đã khởi hành", "Mô phỏng chuyến đã bắt đầu theo lịch.",
                "trip:" + tripId + ":auto-start", now);
        commands.notifyAdmin(dispatch, NotificationType.TRIP_AUTO_STARTED, "Chuyến tự khởi hành",
                "Tài xế đã sẵn sàng và mô phỏng đang chạy.", now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStartFailed(long tripId) {
        TripEntity trip = trips.findLockedById(tripId).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.SCHEDULED) return;
        TripDispatchEntity dispatch = dispatches.findById(tripId).orElse(null);
        if (dispatch == null || dispatch.getState() != DispatchState.READY) return;
        Instant now = operationsClock.instant();
        dispatch.attention(DispatchAttentionCode.START_FAILED, now);
        commands.event(dispatch, DispatchEventKind.ATTENTION_CHANGED, DispatchActorKind.SYSTEM,
                null, null, null, null, "START_FAILED", now);
        commands.notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Tự khởi hành thất bại",
                "Cần admin kiểm tra tuyến, mô phỏng và nguồn lực.", now);
    }

    private void cancelPending(TripEntity trip, Instant now) {
        offers.findByDispatchTripIdAndStatus(trip.getId(), DispatchOfferStatus.PENDING)
                .ifPresent(offer -> {
                    offer.resolve(DispatchOfferStatus.CANCELLED, now, null);
                    commands.sendInbox(offer.getCandidateDriverId(), trip.getId(), offer.getId(),
                            DriverInboxKind.OFFER_CANCELLED, "Lời mời đã hủy",
                            "Lời mời không còn hiệu lực.", "offer:" + offer.getId() + ":cancelled", now);
                });
    }
}
