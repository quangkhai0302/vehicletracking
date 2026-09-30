package com.quangkhai.vehicletracking_backend.dispatch.dto;

import com.quangkhai.vehicletracking_backend.dispatch.entity.DispatchOfferStatus;
import java.util.UUID;

public record DriverOfferActionResponse(UUID offerId, DispatchOfferStatus status, long tripId,
                                        DriverDispatchDetail dispatch) {}
