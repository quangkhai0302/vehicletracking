<script setup lang="ts">
import { computed } from 'vue';
import { Clock3, Gauge, MapPin, Radio, TriangleAlert } from '@lucide/vue';
import { useTripEta } from '@/features/fleet/composables/useTripEta';
import type { TripStop } from '@/features/fleet/types/fleet';
import type {
  SimulationRun,
  StreamConnection,
  TelemetryPosition,
} from '@/features/tracking/types/operations';
import { positionFreshness } from '@/features/tracking/types/operations';
import {
  remainingTime,
  trafficSourceLabel,
  tripTrafficView,
} from '@/features/traffic/utils/tripTraffic';
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
    ? 'Dữ liệu giao thông gần nhất'
    : trafficSourceLabel[view.value.source],
);
const etaText = computed(() =>
  view.value.finished
    ? 'Đã kết thúc'
    : view.value.blocked
      ? 'Đang bị chặn'
      : remainingTime(view.value.countdown),
);
const movementState = computed(() => {
  if (view.value.finished) return { label: 'Đã hoàn thành', tone: 'completed' };
  if (view.value.blocked) return { label: 'Tuyến bị chặn', tone: 'danger' };
  if (props.run?.status === 'PAUSED') return { label: 'Đang tạm dừng', tone: 'paused' };
  if (!sample.value) return { label: 'Chờ vị trí', tone: 'waiting' };
  if (oldPosition.value || props.connection !== 'live')
    return { label: 'Mất cập nhật trực tiếp', tone: 'stale' };
  if ((speed.value ?? 0) < 0.5) return { label: 'Xe đang dừng', tone: 'stopped' };
  return { label: 'Đang di chuyển', tone: 'moving' };
});
</script>

<template>
  <section
    class="trip-traffic-card"
    :data-state="movementState.tone"
    :aria-label="
      context === 'simulation' ? 'Vị trí, vận tốc và ETA mô phỏng' : 'Vị trí, vận tốc và ETA xe'
    "
  >
    <div class="trip-traffic-hud">
      <article class="trip-traffic-metric">
        <span><Gauge :size="14" /> Vận tốc</span>
        <strong :data-testid="`${context}-speed`">
          {{ speed === null ? '—' : speed.toFixed(1) }}
          <small>km/h</small>
        </strong>
      </article>
      <article class="trip-traffic-metric trip-traffic-eta">
        <span><Clock3 :size="14" /> Đến trạm kế tiếp</span>
        <strong :data-testid="`${context}-eta`">{{ etaText }}</strong>
        <small v-if="view.etaAt && run?.status !== 'PAUSED'">
          Lúc {{ displayTripTime(view.etaAt) }}
        </small>
      </article>
    </div>

    <div class="trip-traffic-stop">
      <span
        class="trip-traffic-stop-icon"
        aria-hidden="true"
        ><MapPin :size="16"
      /></span>
      <div>
        <small>{{ view.finished ? 'Điểm kết thúc' : 'Trạm kế tiếp' }}</small>
        <strong>{{ view.finished ? 'Chuyến đã hoàn thành' : stopName }}</strong>
      </div>
      <span
        v-if="view.nextStopSequence !== null"
        class="trip-traffic-stop-sequence"
      >
        #{{ view.nextStopSequence }}
      </span>
    </div>

    <div class="trip-traffic-health">
      <span :data-tone="movementState.tone">
        <Radio :size="12" />
        {{ movementState.label }}
      </span>
      <span :data-stale="staleTraffic || view.source === 'ROUTE_SNAPSHOT'">
        {{ eta.loading && !eta.data && !run?.traffic ? 'Đang tính ETA…' : sourceText }}
      </span>
    </div>

    <div
      v-if="view.impacts.length > 0"
      class="trip-traffic-impact-list"
    >
      <span
        v-for="impact in view.impacts"
        :key="impact"
      >
        <TriangleAlert :size="12" />
        {{ impact }}
      </span>
    </div>

    <p
      v-if="view.blocked"
      class="trip-traffic-alert"
      role="status"
    >
      Đường phía trước bị chặn, chưa thể tính thời gian đến trạm.
    </p>
    <p
      v-if="view.delay !== null && view.delay >= 60"
      class="trip-traffic-delay"
    >
      Chậm hơn khoảng {{ remainingTime(Math.ceil(view.delay)) }} so với thời gian dự kiến.
    </p>
    <p
      v-if="!sample"
      class="trip-traffic-empty"
    >
      Chưa có vị trí xe; bắt đầu chuyến để theo dõi.
    </p>
    <p
      v-if="eta.error && !view.finished"
      class="trip-traffic-alert"
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

    <details class="trip-traffic-details">
      <summary>Dữ liệu và kỹ thuật</summary>
      <p v-if="view.warning && !view.blocked && !view.finished">
        {{
          view.warning.startsWith('VEHICLE_POSITION_UNAVAILABLE')
            ? 'ETA đang dựa trên lịch đã lưu do chưa xác định được xe trên tuyến.'
            : 'Một phần ETA đang dựa trên tuyến đã lưu do dữ liệu giao thông chưa đầy đủ.'
        }}
      </p>
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
        Giao thông cập nhật: {{ displayTripTime(view.observedAt ?? view.fetchedAt) }}
      </p>
    </details>
  </section>
</template>
