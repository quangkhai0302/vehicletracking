<script setup lang="ts">
import { computed } from 'vue';
import { Gauge, MapPin } from '@lucide/vue';
import { useTripEta } from '@/features/fleet/composables/useTripEta';
import type { TripStop } from '@/features/fleet/types/fleet';
import type { SimulationRun, StreamConnection, TelemetryPosition } from '@/features/tracking/types/operations';
import { positionFreshness } from '@/features/tracking/types/operations';
import { remainingTime, trafficSourceLabel, tripTrafficView } from '@/features/traffic/utils/tripTraffic';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
const props = withDefaults(
  defineProps<{
    tripId: number;
    run: SimulationRun | null;
    stops?: TripStop[];
    position?: TelemetryPosition | null;
    now: number;
    connection: StreamConnection;
    context?: 'simulation' | 'vehicle';
  }>(),
  { stops: () => [], context: 'simulation' },
);
const eta = useTripEta(() => props.tripId);
const view = computed(() => tripTrafficView(eta.data, props.run));
const sample = computed(() => (props.context === 'simulation' ? props.run?.frame : props.position));
const speed = computed(() =>
  sample.value && Number.isFinite(sample.value.speedKmh) ? sample.value.speedKmh : null,
);
const sampleAt = computed(() =>
  props.context === 'simulation' ? props.run?.updatedAt : props.position?.recordedAt,
);
const oldPosition = computed(() =>
  props.context === 'simulation'
    ? !!sampleAt.value && props.now - Date.parse(sampleAt.value) > 15_000
    : !!props.position && positionFreshness(props.position, props.now) !== 'fresh',
);
const staleTraffic = computed(
  () =>
    view.value.status === 'STALE' ||
    view.value.source === 'HERE_LAST_KNOWN' ||
    (!!(view.value.observedAt ?? view.value.fetchedAt) &&
      props.now - Date.parse((view.value.observedAt ?? view.value.fetchedAt)!) > 90_000) ||
    !!eta.error,
);
const stopName = computed(
  () =>
    view.value.stationName ??
    props.stops.find((stop) => stop.sequenceNumber === view.value.nextStopSequence)?.stationName ??
    (view.value.nextStopSequence !== null
      ? `Trạm ${view.value.nextStopSequence}`
      : 'Chưa xác định trạm tiếp theo'),
);
const sourceText = computed(() =>
  staleTraffic.value && view.value.source === 'HERE_LIVE'
    ? 'Ước tính theo dữ liệu giao thông gần nhất'
    : trafficSourceLabel[view.value.source],
);
</script>
<template>
  <section
    class="trip-traffic-card"
    :aria-label="
      context === 'simulation' ? 'Vị trí, vận tốc và ETA mô phỏng' : 'Vị trí, vận tốc và ETA xe'
    "
  >
    <div class="telemetry-grid">
      <div>
        <span
          ><Gauge :size="13" />
          {{ oldPosition || connection !== 'live' ? 'Vận tốc gần nhất' : 'Vận tốc xe' }}</span
        ><strong :data-testid="`${context}-speed`"
          >{{ speed === null ? '—' : speed.toFixed(1) }} <small>km/h</small></strong
        >
      </div>
      <div>
        <span>Còn khoảng tới trạm</span
        ><strong :data-testid="`${context}-eta`">{{
          view.finished
            ? 'Đã kết thúc'
            : view.blocked
              ? 'Đường bị chặn'
              : remainingTime(view.countdown)
        }}</strong>
      </div>
    </div>
    <div class="trip-traffic-next">
      <MapPin :size="14" /><strong>{{ view.finished ? 'Chuyến đã kết thúc' : stopName }}</strong>
    </div>
    <p v-if="view.etaAt && run?.status !== 'PAUSED'">
      Dự kiến đến: {{ displayTripTime(view.etaAt) }}
    </p>
    <p v-if="!sample">Chưa có vị trí xe; bắt đầu chuyến để theo dõi.</p>
    <p
      v-if="staleTraffic || view.source !== 'HERE_LIVE' || (eta.loading && !eta.data)"
      class="trip-traffic-source"
      :data-stale="staleTraffic || view.source === 'ROUTE_SNAPSHOT'"
    >
      {{ eta.loading && !eta.data && !run?.traffic ? 'Đang tính giờ đến…' : sourceText }}
    </p>
    <p
      v-if="view.blocked"
      class="trip-traffic-blocked"
      role="status"
    >
      Đường phía trước bị chặn. Chưa thể xác định thời gian đến trạm.
    </p>
    <p
      v-if="view.impacts.length > 0"
      class="trip-traffic-impacts"
    >
      Ảnh hưởng trên phần tuyến còn lại: {{ view.impacts.join(' · ') }}.
    </p>
    <p v-if="view.delay !== null && view.delay >= 60">
      Phần tuyến còn lại chậm hơn khoảng {{ remainingTime(Math.ceil(view.delay)) }} so với tuyến đã
      lưu.
    </p>
    <p v-if="view.warning && !view.blocked && !view.finished">
      {{
        view.warning.startsWith('VEHICLE_POSITION_UNAVAILABLE')
          ? 'Chưa xác định được vị trí xe trên tuyến; ETA dựa trên lịch đã lưu.'
          : 'Giao thông chưa đủ dữ liệu; một phần ETA có thể dựa trên tuyến đã lưu.'
      }}
    </p>
    <p
      v-if="eta.error && !view.finished"
      class="trip-traffic-impacts"
      role="status"
    >
      Chưa làm mới được ETA.
      <button
        type="button"
        @click="eta.retry"
      >
        Thử lại
      </button>
    </p>
    <p v-if="(oldPosition || connection !== 'live') && !view.finished">
      Vị trí chưa được cập nhật trực tiếp.
    </p>
    <p v-if="run?.status === 'PAUSED'">
      Mô phỏng đang tạm dừng; thời gian tới trạm áp dụng khi tiếp tục chạy.
    </p>
    <details class="trip-traffic-details">
      <summary>Thông tin kỹ thuật</summary>
      <p
        v-if="sample"
        class="trip-traffic-position"
      >
        Vị trí: {{ sample.latitude.toFixed(5) }}, {{ sample.longitude.toFixed(5) }} ·
        {{ context === 'simulation' || position?.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS' }}
      </p>
      <p
        v-if="sampleAt"
        class="trip-traffic-update"
      >
        Vị trí cập nhật: {{ displayTripTime(sampleAt) }}
      </p>
      <p
        v-if="view.fetchedAt"
        class="trip-traffic-update"
      >
        Giao thông cập nhật: {{ displayTripTime(view.observedAt ?? view.fetchedAt) }} · ETA làm mới
        mỗi 10 giây.
      </p>
    </details>
  </section>
</template>
