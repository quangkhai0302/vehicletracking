package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchDetail(long tripId, DispatchSummary summary, Long primaryDriverId,
                             Long currentDriverId, List<Candidate> candidates, Offer activeOffer,
                             List<Event> history) {
    public record Candidate(long driverId, String fullName, int priority) {}
    public record Offer(UUID id, long driverId, Instant expiresAt) {}
    public record Event(long revision, DispatchEventKind kind, DispatchActorKind actorKind,
                        Long fromDriverId, Long toDriverId, String reason, Instant createdAt) {}
}
