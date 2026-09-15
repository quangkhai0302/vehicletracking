package com.quangkhai.vehicletracking_backend.traffic;

import com.quangkhai.vehicletracking_backend.config.HereTrafficProperties;
import com.quangkhai.vehicletracking_backend.traffic.service.TrafficQueryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficQueryServiceTest {

    @Test
    void flow_reusesFreshCacheAndFallsBackToLastKnownDataWhenProviderFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T08:00:00Z"));
        HereTrafficProperties properties = properties();
        properties.setCacheTtlSeconds(60);
        properties.setStaleTtlSeconds(300);
        AtomicInteger calls = new AtomicInteger();
        TrafficProvider provider = new TrafficProvider() {
            @Override
            public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) {
                if (calls.incrementAndGet() > 1) throw new TrafficProviderException(TrafficProviderException.Kind.TIMEOUT, "timeout");
                return new TrafficPayload<>(clock.instant(), List.of(new TrafficFlowSegment("flow-1", "Test", 100,
                        List.of(List.of(10.0, 106.0), List.of(10.001, 106.001)), 30, 40, 2, "open", 1.0)));
            }

            @Override
            public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) {
                return new TrafficPayload<>(clock.instant(), List.of());
            }
        };
        TrafficQueryService service = new TrafficQueryService(provider, properties, clock);
        TrafficBounds bounds = TrafficBounds.of(106, 10, 106.01, 10.01, 100);

        assertThat(service.flow(bounds).source()).isEqualTo(TrafficSource.HERE_LIVE);
        assertThat(service.flow(bounds).source()).isEqualTo(TrafficSource.HERE_LIVE);
        assertThat(calls).hasValue(1);

        clock.advanceSeconds(61);
        TrafficEnvelope<TrafficFlowSegment> stale = service.flow(bounds);
        assertThat(stale.source()).isEqualTo(TrafficSource.HERE_LAST_KNOWN);
        assertThat(stale.status()).isEqualTo(TrafficStatus.STALE);
        assertThat(stale.results()).hasSize(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    void flow_whenDisabled_returnsControlledUnavailableError() {
        HereTrafficProperties properties = properties();
        properties.setEnabled(false);
        TrafficProvider provider = new TrafficProvider() {
            @Override
            public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) {
                throw new AssertionError("disabled query must not call provider");
            }

            @Override
            public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) {
                throw new AssertionError("disabled query must not call provider");
            }
        };
        TrafficQueryService service = new TrafficQueryService(provider, properties, Clock.systemUTC());

        assertThatThrownBy(() -> service.flow(TrafficBounds.of(106, 10, 106.01, 10.01, 100)))
                .isInstanceOf(TrafficOperationException.class)
                .extracting(error -> ((TrafficOperationException) error).getErrorCode())
                .isEqualTo(TrafficErrorCode.TRAFFIC_UNAVAILABLE);
    }

    @Test
    void flow_concurrentRequestsShareOneUpstreamCall() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T08:00:00Z"));
        HereTrafficProperties properties = properties();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch upstreamStarted = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        TrafficProvider provider = new TrafficProvider() {
            @Override
            public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) {
                calls.incrementAndGet();
                upstreamStarted.countDown();
                try {
                    releaseUpstream.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new TrafficProviderException(TrafficProviderException.Kind.TIMEOUT, "interrupted");
                }
                return new TrafficPayload<>(clock.instant(), List.of());
            }

            @Override
            public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) {
                return new TrafficPayload<>(clock.instant(), List.of());
            }
        };
        TrafficQueryService service = new TrafficQueryService(provider, properties, clock);
        TrafficBounds bounds = TrafficBounds.of(106, 10, 106.01, 10.01, 100);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TrafficEnvelope<TrafficFlowSegment>> first = executor.submit(() -> service.flow(bounds));
            assertThat(upstreamStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<TrafficEnvelope<TrafficFlowSegment>> second = executor.submit(() -> service.flow(bounds));
            releaseUpstream.countDown();
            assertThat(first.get()).isNotNull();
            assertThat(second.get()).isNotNull();
        }

        assertThat(calls).hasValue(1);
    }

    @Test
    void tile_reusesFreshCacheAndRejectsInvalidCoordinates() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T08:00:00Z"));
        HereTrafficProperties properties = properties();
        properties.setCacheTtlSeconds(60);
        AtomicInteger calls = new AtomicInteger();
        byte[] png = new byte[]{(byte) 137, 80, 78, 71};
        TrafficProvider provider = new TrafficProvider() {
            @Override
            public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) {
                return new TrafficPayload<>(clock.instant(), List.of());
            }

            @Override
            public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) {
                return new TrafficPayload<>(clock.instant(), List.of());
            }

            @Override
            public byte[] fetchTile(int z, int x, int y) {
                calls.incrementAndGet();
                return png;
            }
        };
        TrafficQueryService service = new TrafficQueryService(provider, properties, clock);

        assertThat(service.tile(12, 3261, 1916)).containsExactly(png);
        assertThat(service.tile(12, 3261, 1916)).containsExactly(png);
        assertThat(calls).hasValue(1);

        assertThat(service.tile(-1, 0, 0)).containsExactly(HereTrafficProvider.EMPTY_TILE);
        assertThat(service.tile(12, -1, 1916)).containsExactly(HereTrafficProvider.EMPTY_TILE);
        assertThat(service.tile(12, 4096, 1916)).containsExactly(HereTrafficProvider.EMPTY_TILE);
        assertThat(calls).hasValue(1);
    }

    @Test
    void mapTile_reusesFreshCacheByStyle() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T08:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        byte[] png = new byte[]{(byte) 137, 80, 78, 71};
        TrafficProvider provider = new TrafficProvider() {
            @Override public TrafficPayload<TrafficFlowSegment> fetchFlow(TrafficBounds bounds) { return new TrafficPayload<>(clock.instant(), List.of()); }
            @Override public TrafficPayload<TrafficIncident> fetchIncidents(TrafficBounds bounds) { return new TrafficPayload<>(clock.instant(), List.of()); }
            @Override public RasterTile fetchMapTile(HereMapStyle style, int z, int x, int y) {
                calls.incrementAndGet();
                return new RasterTile(png, "image/png");
            }
        };
        TrafficQueryService service = new TrafficQueryService(provider, properties(), clock);

        assertThat(service.mapTile(HereMapStyle.ROADMAP, 12, 3261, 1916).data()).containsExactly(png);
        assertThat(service.mapTile(HereMapStyle.ROADMAP, 12, 3261, 1916).data()).containsExactly(png);
        assertThat(service.mapTile(HereMapStyle.DARK, 12, 3261, 1916).data()).containsExactly(png);
        assertThat(calls).hasValue(2);
    }

    private HereTrafficProperties properties() {
        var properties = new HereTrafficProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setMaxBboxSpanHundredths(100);
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
