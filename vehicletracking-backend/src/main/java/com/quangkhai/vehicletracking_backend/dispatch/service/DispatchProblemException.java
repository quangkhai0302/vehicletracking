package com.quangkhai.vehicletracking_backend.dispatch.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Stable machine-readable error code for dispatch mutations. */
public class DispatchProblemException extends ResponseStatusException {
    public DispatchProblemException(HttpStatus status, String code, String detail) {
        super(status, detail);
        getBody().setProperty("code", code);
    }

    public static DispatchProblemException conflict(String code) {
        return new DispatchProblemException(HttpStatus.CONFLICT, code, code);
    }

    public static DispatchProblemException invalid(String detail) {
        return new DispatchProblemException(HttpStatus.BAD_REQUEST, "DISPATCH_VALIDATION_FAILED", detail);
    }
}
