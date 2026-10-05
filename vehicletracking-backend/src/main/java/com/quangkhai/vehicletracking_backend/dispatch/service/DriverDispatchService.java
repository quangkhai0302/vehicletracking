package com.quangkhai.vehicletracking_backend.dispatch.service;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig.UserAccountPrincipal;
import com.quangkhai.vehicletracking_backend.dispatch.dto.DriverInboxResponse;
import com.quangkhai.vehicletracking_backend.dispatch.entity.DriverInboxKind;
import com.quangkhai.vehicletracking_backend.dispatch.repository.DriverDispatchInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class DriverDispatchService {
    private final DriverDispatchInboxRepository inbox;
    private final Clock operationsClock;

    @Transactional(readOnly = true)
    public List<DriverInboxResponse> inbox(UserAccountPrincipal principal, int limit) {
        if (limit < 1 || limit > 50) throw DispatchProblemException.invalid("limit phải từ 1 đến 50.");
        return inbox.findAssignmentInbox(driverId(principal),
                List.of(DriverInboxKind.DIRECT_ASSIGNMENT_REQUESTED, DriverInboxKind.DIRECT_ASSIGNMENT_CANCELLED,
                        DriverInboxKind.DIRECT_ASSIGNMENT_ACCEPTED), DriverInboxKind.TRIP_UNASSIGNED,
                PageRequest.of(0, limit))
                .stream().map(DriverInboxResponse::from).toList();
    }

    @Transactional
    public DriverInboxResponse markRead(UserAccountPrincipal principal, long inboxId) {
        var item = inbox.findByIdAndRecipientDriverId(inboxId, driverId(principal))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy thông báo."));
        item.markRead(operationsClock.instant());
        return DriverInboxResponse.from(item);
    }

    private long driverId(UserAccountPrincipal principal) {
        if (principal == null || principal.driverId() == null)
            throw new ResponseStatusException(FORBIDDEN, "Tài khoản chưa được gắn hồ sơ tài xế.");
        return principal.driverId();
    }
}
