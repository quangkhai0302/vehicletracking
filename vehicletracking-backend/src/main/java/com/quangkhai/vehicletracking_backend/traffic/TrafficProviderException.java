package com.quangkhai.vehicletracking_backend.traffic;

public class TrafficProviderException extends RuntimeException {
    public enum Kind { UNAUTHORIZED, UNAVAILABLE, TIMEOUT, INVALID_RESPONSE }

    private final Kind kind;

    public TrafficProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public TrafficProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
