package com.quangkhai.vehicletracking_backend.reroute;

/** User-facing reroute text, including compatibility with persisted legacy messages. */
public final class RerouteMessages {
    public static final String UNAVAILABLE = "Chưa tìm được tuyến thay thế phù hợp hoặc nhanh hơn tuyến hiện tại.";
    public static final String ROAD_CLOSED = "Phát hiện đường bị đóng hoặc bị chặn.";

    private RerouteMessages() {}

    public static String forDisplay(String reason) {
        return switch (reason) {
            case null -> null;
            case "HERE Routing không trả về tuyến khả dụng hoặc tuyến mới không cải thiện ETA." -> UNAVAILABLE;
            case "Phát hiện đường bị đóng/chặn từ HERE Traffic" -> ROAD_CLOSED;
            default -> reason;
        };
    }
}
