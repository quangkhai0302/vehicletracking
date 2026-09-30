package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import java.time.Instant;

public record DispatchSummary(DispatchStartMode startMode, DispatchState state,
                              DispatchAttentionCode attentionCode, Instant readyAt, Long revision) {
    public static DispatchSummary from(TripDispatchEntity item) {
        return new DispatchSummary(item.getStartMode(), item.getState(), item.getAttentionCode(),
                item.getReadyAt(), item.getRevision());
    }
    public static DispatchSummary legacyManual() {
        return new DispatchSummary(DispatchStartMode.MANUAL, DispatchState.MANUAL, null, null, null);
    }
}
