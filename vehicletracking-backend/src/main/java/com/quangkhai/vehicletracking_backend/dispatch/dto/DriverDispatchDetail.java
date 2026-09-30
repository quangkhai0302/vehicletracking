package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchAttentionCode;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchState;
import java.time.Instant;

public record DriverDispatchDetail(long tripId, DispatchStartMode startMode, DispatchState state,
                                   DispatchAttentionCode attentionCode, Instant readyAt,
                                   Instant cutoffAt, long revision, boolean canReady, boolean canReportUnavailable) {}
