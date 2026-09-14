package com.quangkhai.vehicletracking_backend.traffic;

import org.springframework.http.HttpStatus;

public class TrafficOperationException extends RuntimeException {
    private final HttpStatus status;
    private final TrafficErrorCode errorCode;

    public TrafficOperationException(HttpStatus status, TrafficErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public TrafficOperationException(HttpStatus status, TrafficErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public TrafficErrorCode getErrorCode() {
        return errorCode;
    }
}
