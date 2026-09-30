package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DispatchPolicyRequest(Long expectedRevision, @NotNull DispatchStartMode startMode,
                                    @NotNull Boolean backupEnabled, @NotNull List<Long> backupDriverIds) {}
