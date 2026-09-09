package com.quangkhai.vehiceltracking_backend.exception;

public class RouteConflictException extends RuntimeException {
    public RouteConflictException(String message) {
        super(message);
    }
}
