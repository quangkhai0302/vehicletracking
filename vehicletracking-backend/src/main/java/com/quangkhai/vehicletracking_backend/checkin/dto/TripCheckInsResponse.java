package com.quangkhai.vehicletracking_backend.checkin.dto;

import java.util.List;

public record TripCheckInsResponse(long tripId, long revision, Integer nextStopSequence,
        boolean awaitingExit, List<StopVisitResponse> visits) {}
