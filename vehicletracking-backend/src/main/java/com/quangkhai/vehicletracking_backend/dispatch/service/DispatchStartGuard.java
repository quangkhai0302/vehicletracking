package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchStartMode;
import com.quangkhai.vehicletracking_backend.dispatch.repository.TripDispatchRepository;
import com.quangkhai.vehicletracking_backend.trip.entity.TripStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@RequiredArgsConstructor
public class DispatchStartGuard {
    private final TripDispatchRepository dispatches;

    public void rejectManualFirstStart(long tripId, TripStatus status, int attemptNumber) {
        if (attemptNumber == 1 && status == TripStatus.SCHEDULED && dispatches.findById(tripId)
                .filter(dispatch -> dispatch.getStartMode() == DispatchStartMode.AUTO_IF_READY).isPresent())
            throw new DispatchProblemException(CONFLICT, "DISPATCH_OVERRIDE_REQUIRED",
                    "Chuyến tự động cần xác nhận hoặc thao tác ngoại lệ.");
    }
}
