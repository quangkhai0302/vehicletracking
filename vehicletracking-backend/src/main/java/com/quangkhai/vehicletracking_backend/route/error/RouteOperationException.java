package com.quangkhai.vehicletracking_backend.route.error;

import org.springframework.http.HttpStatus;

public class RouteOperationException extends RuntimeException {

    private final HttpStatus status;
    private final RouteErrorCode errorCode;

    public RouteOperationException(HttpStatus status, RouteErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public RouteOperationException(HttpStatus status, RouteErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public RouteErrorCode getErrorCode() {
        return errorCode;
    }
}
