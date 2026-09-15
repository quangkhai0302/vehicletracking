package com.quangkhai.vehicletracking_backend.traffic;

public interface TrafficProvider {
    TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds);

    TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds);

    default byte[] fetchTile(int z, int x, int y) {
        return new byte[0];
    }

    default RasterTile fetchMapTile(HereMapStyle style, int z, int x, int y) {
        return new RasterTile(new byte[0], "image/png");
    }

    default RasterTile fetchVectorStyle(HereVectorStyle style) {
        return new RasterTile(new byte[0], "application/json");
    }

    default RasterTile fetchVectorResource(String upstreamUrl) {
        return new RasterTile(new byte[0], "application/octet-stream");
    }

    record RasterTile(byte[] data, String contentType) {
        public RasterTile {
            data = data == null ? new byte[0] : data.clone();
            contentType = contentType == null || contentType.isBlank() ? "image/png" : contentType;
        }
    }

    record TrafficPayload<T>(java.time.Instant observedAt, java.util.List<T> results) {
        public TrafficPayload {
            results = results == null ? java.util.List.of() : java.util.List.copyOf(results);
        }
    }
}
