package com.quangkhai.vehicletracking_backend.route.provider;

import com.quangkhai.vehicletracking_backend.route.error.RouteErrorCode;
import com.quangkhai.vehicletracking_backend.route.error.RouteOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Shared cost/concurrency boundary for every Google Routes HTTP subrequest. */
@Component
public class GoogleRoutingRequestGuard {
    private final GoogleRoutingProperties properties;
    private final Clock clock;
    private final Semaphore concurrency;
    private final Deque<Instant> minuteRequests = new ArrayDeque<>();
    private LocalDate countedDate;
    private int dailyRequests;

    public GoogleRoutingRequestGuard(GoogleRoutingProperties properties, Clock operationsClock) {
        this.properties = properties;
        this.clock = operationsClock;
        this.concurrency = new Semaphore(properties.getMaxConcurrentRequests(), true);
    }

    public <T> T execute(Supplier<T> request) {
        if (!concurrency.tryAcquire()) throw unavailable("Google Routes đang đạt giới hạn request đồng thời");
        try {
            reserve();
            return request.get();
        } finally {
            concurrency.release();
        }
    }

    private synchronized void reserve() {
        Instant now = clock.instant();
        LocalDate date = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (!date.equals(countedDate)) {
            countedDate = date;
            dailyRequests = 0;
        }
        Instant minuteBoundary = now.minusSeconds(60);
        while (!minuteRequests.isEmpty() && !minuteRequests.getFirst().isAfter(minuteBoundary)) {
            minuteRequests.removeFirst();
        }
        if (dailyRequests >= properties.getMaxRequestsPerDay()) {
            throw unavailable("Google Routes đã đạt ngân sách request trong ngày");
        }
        if (minuteRequests.size() >= properties.getMaxRequestsPerMinute()) {
            throw unavailable("Google Routes đã đạt giới hạn request mỗi phút");
        }
        minuteRequests.addLast(now);
        dailyRequests++;
    }

    private RouteOperationException unavailable(String detail) {
        return new RouteOperationException(HttpStatus.SERVICE_UNAVAILABLE, RouteErrorCode.ROUTING_UNAVAILABLE, detail);
    }
}
