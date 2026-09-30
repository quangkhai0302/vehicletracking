package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.dto.*;
import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import com.quangkhai.vehicletracking_backend.dispatch.repository.*;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.reroute.entity.*;
import com.quangkhai.vehicletracking_backend.reroute.repository.TripNotificationRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripEntity;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import com.quangkhai.vehicletracking_backend.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class DriverDispatchService {
    private final TripRepository trips;
    private final TripDispatchRepository dispatches;
    private final TripDispatchOfferRepository offers;
    private final TripDispatchEventRepository events;
    private final DriverUnavailabilityRepository unavailability;
    private final DriverDispatchInboxRepository inbox;
    private final DriverRepository drivers;
    private final DispatchAvailabilityService availability;
    private final TripNotificationRepository notifications;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public DriverDispatchDetail detail(UserAccountPrincipal principal, long tripId) {
        TripEntity trip = ownTrip(principal, tripId, false);
        return projection(trip, dispatches.findById(tripId).orElse(null), now());
    }

    @Transactional
    public DriverDispatchDetail ready(UserAccountPrincipal principal, long tripId, long expectedRevision) {
        TripEntity trip = ownTrip(principal, tripId, true);
        TripDispatchEntity dispatch = requireAuto(trip);
        checkRevision(dispatch, expectedRevision);
        Instant now = now();
        requireActiveSchedule(dispatch);
        requireWindow(trip, now, true);
        if (dispatch.isReadyFor(trip)) return projection(trip, dispatch, now);
        if (dispatch.getState() != DispatchState.WAITING_READY
                && !(dispatch.getState() == DispatchState.ATTENTION
                     && dispatch.getAttentionCode() == DispatchAttentionCode.DRIVER_NOT_READY))
            throw conflict("DISPATCH_INVALID_STATE");
        if (!availability.driverAvailable(principal.driverId(), trip, dispatch.getBaselineDurationSeconds(), false)
                || !availability.vehicleAvailable(trip.getVehicle().getId(), trip, dispatch.getBaselineDurationSeconds()))
            throw conflict("RESOURCE_CONFLICT");
        dispatch.ready(trip, now);
        event(dispatch, DispatchEventKind.READY, DispatchActorKind.DRIVER, principal.accountId(),
                principal.driverId(), null, principal.driverId(), null, now);
        return projection(trip, dispatch, now);
    }

    @Transactional
    public DriverUnavailableResponse unavailable(UserAccountPrincipal principal, long tripId,
                                                   long expectedRevision, String reason) {
        TripEntity trip = ownTrip(principal, tripId, true);
        TripDispatchEntity dispatch = requireAuto(trip);
        checkRevision(dispatch, expectedRevision);
        Instant now = now();
        requireActiveSchedule(dispatch);
        requireWindow(trip, now, false);
        if (reason == null || reason.trim().length() < 3 || reason.trim().length() > 500)
            throw DispatchProblemException.invalid("Lý do báo bận phải dài từ 3 đến 500 ký tự.");
        long previousDriverId = principal.driverId();
        unavailability.save(new DriverUnavailabilityEntity(dispatch, previousDriverId, reason.trim(), now));
        trip.assignDriver(null);
        dispatch.driverUnavailable(now);
        event(dispatch, DispatchEventKind.BUSY, DispatchActorKind.DRIVER, principal.accountId(),
                previousDriverId, previousDriverId, null, reason.trim(), now);
        notifyAdmin(dispatch, NotificationType.DRIVER_UNAVAILABLE, "Tài xế báo bận",
                "Tài xế không thể thực hiện chuyến: " + reason.trim(), now);
        sendInbox(previousDriverId, tripId, null, DriverInboxKind.TRIP_UNASSIGNED,
                "Đã báo bận", "Bạn không còn được phân công chuyến này.",
                "trip:" + tripId + ":unassigned:" + dispatch.getRevision(), now);
        return new DriverUnavailableResponse(tripId, dispatch.getState(), dispatch.getRevision());
    }

    @Transactional(readOnly = true)
    public List<DriverOfferResponse> offers(UserAccountPrincipal principal) {
        long driverId = driverId(principal);
        Instant now = now();
        return offers.findAllByCandidateDriverIdAndStatusOrderByExpiresAtAsc(driverId, DispatchOfferStatus.PENDING)
                .stream().filter(offer -> now.isBefore(offer.getExpiresAt())
                        && now.isBefore(cutoff(offer.getDispatch().getTrip()))
                        && activeSchedule(offer.getDispatch()))
                .limit(50).map(offer -> {
                    TripEntity trip = offer.getDispatch().getTrip();
                    return new DriverOfferResponse(offer.getId(), trip.getId(), trip.getRoute().getName(),
                            trip.getVehiclePlateSnapshot(), trip.getScheduledDepartureAt(), cutoff(trip),
                            offer.getExpiresAt(), offer.getDispatch().getRevision());
                }).toList();
    }

    @Transactional
    public DriverOfferActionResponse accept(UserAccountPrincipal principal, UUID offerId, long expectedRevision) {
        TripDispatchOfferEntity preview = ownOffer(principal, offerId);
        TripEntity trip = lockedTrip(preview.getDispatch().getTripId());
        TripDispatchOfferEntity offer = ownOffer(principal, offerId);
        TripDispatchEntity dispatch = requireAuto(trip);
        checkRevision(dispatch, expectedRevision);
        Instant now = now();
        requireActiveSchedule(dispatch);
        if (offer.getStatus() != DispatchOfferStatus.PENDING
                || !now.isBefore(offer.getExpiresAt()) || !now.isBefore(cutoff(trip)))
            throw conflict("OFFER_EXPIRED");
        var driver = drivers.findLockedById(principal.driverId())
                .orElseThrow(() -> conflict("DRIVER_UNAVAILABLE"));
        if (!availability.driverAvailable(principal.driverId(), trip, dispatch.getBaselineDurationSeconds(), true)
                || !availability.vehicleAvailable(trip.getVehicle().getId(), trip, dispatch.getBaselineDurationSeconds()))
            throw conflict("RESOURCE_CONFLICT");
        trip.assignDriver(driver);
        offer.resolve(DispatchOfferStatus.ACCEPTED, now, null);
        dispatch.assignmentChanged(now, false);
        event(dispatch, DispatchEventKind.REASSIGNED, DispatchActorKind.DRIVER, principal.accountId(),
                principal.driverId(), null, principal.driverId(), null, now);
        sendInbox(principal.driverId(), trip.getId(), offerId, DriverInboxKind.TRIP_ASSIGNED,
                "Đã nhận chuyến", "Bạn cần xác nhận sẵn sàng trước khi chuyến tự khởi hành.",
                "offer:" + offerId + ":accepted", now);
        notifyAdmin(dispatch, NotificationType.DISPATCH_REASSIGNED, "Đã đổi tài xế",
                "Tài xế dự phòng đã nhận chuyến và cần xác nhận sẵn sàng.", now);
        return new DriverOfferActionResponse(offerId, offer.getStatus(), trip.getId(), projection(trip, dispatch, now));
    }

    @Transactional
    public DriverOfferActionResponse decline(UserAccountPrincipal principal, UUID offerId,
                                              long expectedRevision, String reason) {
        TripDispatchOfferEntity preview = ownOffer(principal, offerId);
        TripEntity trip = lockedTrip(preview.getDispatch().getTripId());
        TripDispatchOfferEntity offer = ownOffer(principal, offerId);
        TripDispatchEntity dispatch = requireAuto(trip);
        checkRevision(dispatch, expectedRevision);
        Instant now = now();
        requireActiveSchedule(dispatch);
        if (offer.getStatus() != DispatchOfferStatus.PENDING || !now.isBefore(offer.getExpiresAt()))
            throw conflict("OFFER_EXPIRED");
        String normalized = reason == null ? null : reason.trim();
        if (normalized != null && (normalized.length() < 3 || normalized.length() > 500))
            throw DispatchProblemException.invalid("Lý do từ chối phải dài từ 3 đến 500 ký tự.");
        offer.resolve(DispatchOfferStatus.DECLINED, now, normalized);
        dispatch.searchNow(now);
        event(dispatch, DispatchEventKind.OFFER_DECLINED, DispatchActorKind.DRIVER, principal.accountId(),
                principal.driverId(), null, null, normalized, now);
        return new DriverOfferActionResponse(offerId, offer.getStatus(), trip.getId(), null);
    }

    @Transactional(readOnly = true)
    public List<DriverInboxResponse> inbox(UserAccountPrincipal principal, int limit) {
        if (limit < 1 || limit > 50) throw DispatchProblemException.invalid("limit phải từ 1 đến 50.");
        return inbox.findByRecipientDriverIdOrderByCreatedAtDescIdDesc(driverId(principal), PageRequest.of(0, limit))
                .stream().map(DriverInboxResponse::from).toList();
    }

    @Transactional
    public DriverInboxResponse markRead(UserAccountPrincipal principal, long inboxId) {
        DriverDispatchInboxEntity item = inbox.findByIdAndRecipientDriverId(inboxId, driverId(principal))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy thông báo."));
        item.markRead(now());
        return DriverInboxResponse.from(item);
    }

    public void openNextOffer(TripEntity trip, TripDispatchEntity dispatch, Instant now) {
        HashSet<Long> attempted = new HashSet<>();
        offers.findAllByDispatchTripIdOrderByOfferedAtAsc(trip.getId())
                .forEach(offer -> attempted.add(offer.getCandidateDriverId()));
        List<Long> candidates = dispatch.isBackupEnabled() ? dispatch.getBackupDriverIds() : List.of();
        for (int i = 0; i < candidates.size(); i++) {
            long candidateId = candidates.get(i);
            if (attempted.contains(candidateId) || drivers.findLockedById(candidateId).isEmpty()) continue;
            if (!availability.driverAvailable(candidateId, trip, dispatch.getBaselineDurationSeconds(), false)) continue;
            Instant expiry = now.plusSeconds(120);
            if (expiry.isAfter(cutoff(trip))) expiry = cutoff(trip);
            if (!expiry.isAfter(now)) break;
            dispatch.markOfferPending(expiry, now);
            TripDispatchOfferEntity offer = offers.save(new TripDispatchOfferEntity(dispatch, candidateId,
                    (short) (i + 1), now, expiry));
            event(dispatch, DispatchEventKind.OFFERED, DispatchActorKind.SYSTEM, null, null,
                    null, candidateId, null, now);
            sendInbox(candidateId, trip.getId(), offer.getId(), DriverInboxKind.OFFER_RECEIVED,
                    "Lời mời nhận chuyến", "Vui lòng nhận hoặc từ chối trước khi lời mời hết hạn.",
                    "offer:" + offer.getId() + ":received", now);
            return;
        }
        dispatch.attention(DispatchAttentionCode.NO_BACKUP, now);
        event(dispatch, DispatchEventKind.ATTENTION_CHANGED, DispatchActorKind.SYSTEM, null, null,
                null, null, "NO_BACKUP", now);
        notifyAdmin(dispatch, NotificationType.DISPATCH_ATTENTION, "Chuyến cần điều phối",
                "Đã hết tài xế dự phòng phù hợp.", now);
    }

    public void expirePending(TripEntity trip, TripDispatchEntity dispatch, TripDispatchOfferEntity offer, Instant now) {
        offer.resolve(DispatchOfferStatus.EXPIRED, now, null);
        dispatch.searchNow(now);
        event(dispatch, DispatchEventKind.OFFER_EXPIRED, DispatchActorKind.SYSTEM, null, null,
                null, offer.getCandidateDriverId(), null, now);
        sendInbox(offer.getCandidateDriverId(), trip.getId(), offer.getId(), DriverInboxKind.OFFER_EXPIRED,
                "Lời mời đã hết hạn", "Bạn không còn có thể nhận lời mời này.",
                "offer:" + offer.getId() + ":expired", now);
    }

    public void notifyAdmin(TripDispatchEntity dispatch, NotificationType type, String title,
                            String reason, Instant now) {
        String dedupe = "trip:" + dispatch.getTripId() + ":dispatch:" + dispatch.getRevision() + ":" + type;
        if (notifications.findByDedupeKey(dedupe).isPresent()) return;
        notifications.save(new TripNotificationEntity(dispatch.getTrip(), null, type, NotificationSeverity.MAJOR,
                title, reason.substring(0, Math.min(255, reason.length())), null, "", null, null, dedupe, now));
    }

    public void event(TripDispatchEntity dispatch, DispatchEventKind kind, DispatchActorKind actor,
                      Long accountId, Long actorDriverId, Long fromDriverId, Long toDriverId,
                      String reason, Instant now) {
        events.save(new TripDispatchEventEntity(dispatch, kind, actor, accountId, actorDriverId,
                fromDriverId, toDriverId, reason, now));
    }

    public void sendInbox(long recipientId, long tripId, UUID offerId, DriverInboxKind kind,
                          String title, String detail, String dedupeKey, Instant now) {
        if (!inbox.existsByDedupeKey(dedupeKey))
            inbox.save(new DriverDispatchInboxEntity(recipientId, tripId, offerId, kind,
                    title, detail, dedupeKey, now));
    }

    public void checkRevision(TripDispatchEntity dispatch, long expected) {
        if (dispatch.getRevision() != expected) throw conflict("DISPATCH_STALE_REVISION");
    }
    public void requireActiveSchedule(TripDispatchEntity dispatch) {
        if (!activeSchedule(dispatch)) throw conflict("SCHEDULE_DISABLED");
    }
    private boolean activeSchedule(TripDispatchEntity dispatch) {
        return dispatch.getTrip().getSchedule() != null && dispatch.getTrip().getSchedule().isEnabled()
                && dispatch.getTrip().getSchedule().getDispatchEpoch() == dispatch.getScheduleEpoch();
    }
    private TripDispatchEntity requireAuto(TripEntity trip) {
        TripDispatchEntity dispatch = dispatches.findById(trip.getId())
                .orElseThrow(() -> conflict("DISPATCH_INVALID_STATE"));
        if (dispatch.getStartMode() != DispatchStartMode.AUTO_IF_READY || trip.getStatus() != TripStatus.SCHEDULED)
            throw conflict("DISPATCH_INVALID_STATE");
        return dispatch;
    }
    private DriverDispatchDetail projection(TripEntity trip, TripDispatchEntity dispatch, Instant now) {
        if (dispatch == null) return new DriverDispatchDetail(trip.getId(), DispatchStartMode.MANUAL,
                DispatchState.MANUAL, null, null, cutoff(trip), 0, false, false);
        boolean eligible = dispatch.getStartMode() == DispatchStartMode.AUTO_IF_READY
                && trip.getStatus() == TripStatus.SCHEDULED && activeSchedule(dispatch)
                && now.isBefore(cutoff(trip));
        return new DriverDispatchDetail(trip.getId(), dispatch.getStartMode(), dispatch.getState(),
                dispatch.getAttentionCode(), dispatch.getReadyAt(), cutoff(trip), dispatch.getRevision(),
                eligible && !now.isBefore(trip.getScheduledDepartureAt().minusSeconds(30 * 60))
                        && (dispatch.getState() == DispatchState.WAITING_READY
                            || dispatch.getAttentionCode() == DispatchAttentionCode.DRIVER_NOT_READY),
                eligible);
    }
    private TripEntity ownTrip(UserAccountPrincipal principal, long id, boolean lock) {
        long owner = driverId(principal);
        TripEntity trip = lock ? lockedTrip(id) : trips.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến."));
        if (trip.getDriver() == null || trip.getDriver().getId() != owner)
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến được phân công.");
        return trip;
    }
    private TripEntity lockedTrip(long id) {
        return trips.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy chuyến."));
    }
    private TripDispatchOfferEntity ownOffer(UserAccountPrincipal principal, UUID id) {
        TripDispatchOfferEntity offer = offers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lời mời."));
        if (offer.getCandidateDriverId() != driverId(principal))
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy lời mời.");
        return offer;
    }
    private long driverId(UserAccountPrincipal principal) {
        if (principal == null || principal.driverId() == null)
            throw new ResponseStatusException(FORBIDDEN, "Tài khoản chưa được gắn hồ sơ tài xế.");
        return principal.driverId();
    }
    private void requireWindow(TripEntity trip, Instant now, boolean ready) {
        if (!now.isBefore(cutoff(trip))) throw conflict("DISPATCH_WINDOW_CLOSED");
        if (ready && now.isBefore(trip.getScheduledDepartureAt().minusSeconds(30 * 60)))
            throw conflict("DISPATCH_WINDOW_CLOSED");
    }
    private Instant cutoff(TripEntity trip) { return trip.getScheduledDepartureAt().plusSeconds(15 * 60); }
    private Instant now() { return operationsClock.instant(); }
    private ResponseStatusException conflict(String code) { return DispatchProblemException.conflict(code); }
}
