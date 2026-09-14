package com.quangkhai.vehicletracking_backend.traffic;

public interface TrafficProvider {
    TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds);

    TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds);

    default byte[] fetchTile(int z, int x, int y) {
        return new byte[0];
    }

    record TrafficPayload<T>(java.time.Instant observedAt, java.util.List<T> results) {
        public TrafficPayload {
            results = results == null ? java.util.List.of() : java.util.List.copyOf(results);
        }
    }
}
