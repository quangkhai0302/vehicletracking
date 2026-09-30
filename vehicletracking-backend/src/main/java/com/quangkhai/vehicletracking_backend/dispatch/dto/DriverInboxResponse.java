package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverDispatchInboxEntity;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverInboxKind;
import java.time.Instant;
import java.util.UUID;

public record DriverInboxResponse(long id, DriverInboxKind type, String title, String detail, long tripId,
                                  UUID offerId, Instant createdAt, Instant readAt) {
    public static DriverInboxResponse from(DriverDispatchInboxEntity item) {
        return new DriverInboxResponse(item.getId(), item.getKind(), item.getTitle(), item.getDetail(),
                item.getTripId(), item.getOfferId(), item.getCreatedAt(), item.getReadAt());
    }
}
