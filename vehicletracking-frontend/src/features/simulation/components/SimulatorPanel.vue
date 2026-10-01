<script setup lang="ts">
import { computed, defineAsyncComponent, defineComponent, h, ref } from 'vue';
import {
  Car,
  Check,
  Clock3,
  Gauge,
  MapPin,
  Navigation,
  Pause,
  Play,
  RotateCcw,
  Route as RouteIcon,
  Square,
  UserRound,
} from '@lucide/vue';
import type { useSimulator } from '@/features/simulation/composables/useSimulator';
import type { useSimulationFleet } from '@/features/simulation/composables/useSimulationFleet';
import type {
  OperationsSnapshot,
  SimulationStatus,
  StreamConnection,
} from '@/features/tracking/types/operations';
import { SIMULATION_LABELS } from '@/features/tracking/types/operations';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import '@/features/simulation/styles/simulator.css';

const SimulationFleetList = defineAsyncComponent({
  loader: () => import('./SimulationFleetList.vue'),
  delay: 0,
  loadingComponent: defineComponent({ setup: () => () => h('p', 'Đang mở đội xe…') }),
});

const props = defineProps<{
  simulator: ReturnType<typeof useSimulator>;
  snapshot: OperationsSnapshot | null;
  connection: StreamConnection;
  connectionError: string | null;
  onReconnect: () => void;
  onShowRoute: () => void;
  now: number;
  fleet?: ReturnType<typeof useSimulationFleet>;
  onSelectVehicle?: (tripId: number) => void;
  onFitFleet?: () => void;
  onManageFleet?: () => void;
  onShowStop?: (sequenceNumber: number) => void;
}>();

const confirm = ref<'stop' | 'reset' | null>(null);
const trip = computed(() => props.simulator.trip);
const run = computed(() => props.simulator.run);
const detail = computed(() => props.simulator.detail);
const busy = computed(() => props.simulator.busy);
const loading = computed(() => props.simulator.loading);
const running = computed(() => run.value?.status === 'RUNNING');
const active = computed(
  () => trip.value?.status === 'SCHEDULED' || trip.value?.status === 'IN_PROGRESS',
);
const usingGps = computed(() =>
  props.snapshot?.positions.some(
    (point) => point.tripId === trip.value?.id && point.source === 'GPS',
  ),
);
const canPlay = computed(
  () =>
    !!trip.value &&
    !usingGps.value &&
    active.value &&
    (!run.value || run.value.status === 'PAUSED' || running.value),
);
const frame = computed(() => run.value?.frame);
const options = computed(() => props.snapshot?.trips ?? []);
const currentCheckIns = computed(() =>
  props.snapshot?.checkIns.find((item) => item.tripId === trip.value?.id),
);
const currentAttempt = computed(
  () => run.value?.attemptNumber ?? trip.value?.attemptNumber ?? null,
);
const currentVisits = computed(() => {
  const attempt = currentAttempt.value;
  return (
    currentCheckIns.value?.visits.filter(
      (visit) =>
        attempt === null || visit.attemptNumber === undefined || visit.attemptNumber === attempt,
    ) ?? []
  );
});
const visitByStop = computed(
  () => new Map(currentVisits.value.map((visit) => [visit.stopSequence, visit])),
);
const completedStops = computed(() => visitByStop.value.size);
const orderedStops = computed(() =>
  [...(detail.value?.stops ?? [])].sort((a, b) => a.sequenceNumber - b.sequenceNumber),
);
const totalStops = computed(() => orderedStops.value.length);
const startStop = computed(() => orderedStops.value[0] ?? null);
const endStop = computed(() => {
  const stops = orderedStops.value;
  return stops?.[stops.length - 1] ?? null;
});
const nextStop = computed(() => {
  const sequence = frame.value?.nextStopSequence ?? currentCheckIns.value?.nextStopSequence;
  if (sequence) return orderedStops.value.find((stop) => stop.sequenceNumber === sequence) ?? null;
  if (frame.value?.finished || trip.value?.status === 'COMPLETED') return null;
  return orderedStops.value.find((stop) => !visitByStop.value.has(stop.sequenceNumber)) ?? null;
});
const stopState = (sequenceNumber: number) =>
  visitByStop.value.has(sequenceNumber)
    ? 'checked-in'
    : nextStop.value?.sequenceNumber === sequenceNumber
      ? 'next'
      : 'pending';
const stopRoleLabel = (index: number) =>
  index === 0 ? 'Điểm đầu' : index === orderedStops.value.length - 1 ? 'Điểm cuối' : 'Trạm dừng';
const isNotStarted = computed(
  () =>
    completedStops.value === 0 &&
    trip.value?.status === 'SCHEDULED' &&
    (!run.value || run.value.elapsedSeconds === 0),
);
const stopStatusLabel = (sequenceNumber: number, index: number) => {
  if (visitByStop.value.has(sequenceNumber)) return 'Đã check-in';
  if (isNotStarted.value) {
    if (index === 0) return 'Trạm đầu';
    if (index === orderedStops.value.length - 1) return 'Trạm cuối';
  }
  return stopState(sequenceNumber) === 'next' ? 'Kế tiếp' : 'Chưa check-in';
};
const progressPercent = computed(() => {
  if (frame.value) return Math.min(100, Math.max(0, frame.value.progressPercent));
  if (trip.value?.status === 'COMPLETED') return 100;
  if (!totalStops.value) return 0;
  return Math.min(100, (completedStops.value / totalStops.value) * 100);
});
const currentSpeed = computed(() => Math.max(0, Math.round(frame.value?.speedKmh ?? 0)));
const formatDuration = (seconds: number | null | undefined) => {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return '—';
  const rounded = Math.max(0, Math.ceil(seconds));
  if (rounded < 60) return `${rounded} giây`;
  const minutes = Math.floor(rounded / 60);
  const remaining = rounded % 60;
  return remaining ? `${minutes}p ${remaining}s` : `${minutes} phút`;
};
const nextStopEta = computed(() => {
  if (frame.value?.finished) return 'Đã đến';
  return formatDuration(frame.value?.nextStopEtaSeconds);
});
const dwellTime = computed(() => {
  if (!frame.value?.dwelling || !run.value) return null;
  const simulatedSeconds = Math.max(0, Math.ceil(frame.value.dwellRemainingSeconds));
  return {
    simulatedSeconds,
    realSeconds: Math.max(0, Math.ceil(simulatedSeconds / run.value.multiplier)),
  };
});
const canControl = computed(() => !busy.value && !loading.value && props.connection === 'live');
const playLabel = computed(() =>
  running.value
    ? 'Tạm dừng'
    : run.value?.status === 'PAUSED' && trip.value?.status === 'IN_PROGRESS'
      ? 'Tiếp tục'
      : 'Bắt đầu',
);
const simulationStatusLabel = computed(() => {
  if (usingGps.value) return 'Đang nhận GPS';
  if (run.value) {
    return SIMULATION_LABELS[run.value.status as SimulationStatus] ?? run.value.status;
  }
  return active.value ? 'Sẵn sàng' : 'Chuyến đã kết thúc';
});
const routeMovementLabel = computed(() => {
  if (!run.value) return 'Sẵn sàng tại trạm đầu';
  if (frame.value?.finished) return 'Đã hoàn tất lộ trình';
  if (run.value.status === 'PAUSED') return 'Đang tạm dừng';
  if (frame.value?.dwelling) return 'Đang dừng tại trạm';
  return 'Đang di chuyển trên tuyến';
});
const speeds = [1, 5, 10] as const;

const selectTrip = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value;
  if (value && props.onSelectVehicle) props.onSelectVehicle(Number(value));
  else props.simulator.select(value ? Number(value) : null);
};

const confirmCommand = () => {
  if (confirm.value)
    void props.simulator.command(confirm.value).then((ok) => {
      if (ok) confirm.value = null;
    });
};
</script>

<template>
  <section
    class="simulator-content"
    aria-label="Điều khiển mô phỏng"
  >
    <div
      class="simulation-connection"
      :data-state="connection"
      :hidden="connection === 'live'"
    >
      <span>{{
        connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · đang thử lại'
      }}</span>
      <button
        v-if="connection !== 'live'"
        @click="onReconnect"
      >
        Kết nối lại
      </button>
    </div>

    <SimulationFleetList
      v-if="fleet && onSelectVehicle && onFitFleet && onManageFleet"
      :fleet="fleet"
      :snapshot="snapshot"
      :selected-trip-id="simulator.tripId"
      :disabled="busy"
      :on-select="onSelectVehicle"
      :on-fit="onFitFleet"
      :on-manage="onManageFleet"
    />

    <label class="simulation-select">
      <span>Xe và chuyến đang điều khiển</span>
      <select
        aria-label="Chọn chuyến mô phỏng"
        :disabled="busy || !snapshot"
        :value="simulator.tripId ?? ''"
        @change="selectTrip"
      >
        <option value="">Chọn xe và chuyến đi…</option>
        <option
          v-if="simulator.tripId && !options.some((item) => item.id === simulator.tripId)"
          :value="simulator.tripId"
        >
          Chuyến #{{ simulator.tripId }}
        </option>
        <option
          v-for="item in options"
          :key="item.id"
          :value="item.id"
        >
          #{{ item.id }} · {{ item.vehiclePlateNumber }} · {{ item.routeName }}
        </option>
      </select>
    </label>

    <div
      v-if="!trip && !loading"
      class="telemetry-placeholder"
    >
      <Car :size="26" />
      <div>
        <strong>Chưa chọn xe mô phỏng</strong>
        <span>Chọn một xe trên bản đồ hoặc trong danh sách để xem hành trình và điều khiển.</span>
      </div>
    </div>

    <p
      v-if="loading"
      class="availability-note"
      role="status"
    >
      Đang tải dữ liệu chuyến…
    </p>

    <template v-if="trip">
      <div class="simulation-summary">
        <div class="simulation-vehicle-identity">
          <span
            class="simulation-vehicle-icon"
            aria-hidden="true"
            ><Car :size="20"
          /></span>
          <div>
            <small>Phương tiện thực hiện</small>
            <strong>#{{ trip.id }} · {{ trip.vehiclePlateNumber }}</strong>
            <span>{{ trip.routeName }}</span>
          </div>
        </div>
        <span
          class="simulation-status-pill"
          :data-state="run?.status.toLowerCase() ?? (active ? 'ready' : 'completed')"
          data-testid="simulation-status"
        >
          {{ simulationStatusLabel }}
        </span>
      </div>

      <div class="simulation-assignment-grid">
        <div>
          <UserRound :size="17" />
          <span>
            <small>Tài xế</small>
            <strong>{{ trip.driver?.fullName ?? 'Chưa phân công' }}</strong>
          </span>
        </div>
        <div>
          <MapPin :size="17" />
          <span>
            <small>{{ nextStop ? 'Trạm kế tiếp' : 'Vị trí hành trình' }}</small>
            <strong>
              {{
                nextStop?.stationName ??
                (frame?.finished ? endStop?.stationName : startStop?.stationName) ??
                'Đang cập nhật'
              }}
            </strong>
          </span>
        </div>
      </div>

      <div class="simulation-route-card">
        <div class="simulation-route-heading">
          <span><RouteIcon :size="16" /> Hành trình</span>
          <b>{{ Math.round(progressPercent) }}%</b>
        </div>
        <div class="simulation-route-ends">
          <span>{{ startStop?.stationName ?? 'Điểm đầu' }}</span>
          <i aria-hidden="true" />
          <span>{{ endStop?.stationName ?? 'Điểm cuối' }}</span>
        </div>
        <div
          class="progress-track"
          role="progressbar"
          aria-label="Tiến độ tuyến mô phỏng"
          :aria-valuenow="progressPercent"
          :aria-valuemin="0"
          :aria-valuemax="100"
        >
          <span :style="{ width: `${progressPercent}%` }" />
        </div>
        <div class="simulation-route-meta">
          <span>{{ completedStops }}/{{ totalStops || '—' }} trạm đã qua</span>
          <span>{{ routeMovementLabel }}</span>
        </div>
      </div>

      <div
        v-if="run"
        class="simulation-telemetry-grid"
      >
        <div>
          <span><Gauge :size="15" /> Vận tốc</span>
          <strong>{{ currentSpeed }} <small>km/h</small></strong>
        </div>
        <div>
          <span><Clock3 :size="15" /> Đến trạm</span>
          <strong>{{ nextStopEta }}</strong>
        </div>
        <div>
          <span><Navigation :size="15" /> Nhịp phát</span>
          <strong>{{ run.multiplier }}×</strong>
        </div>
      </div>

      <div
        v-if="!run && trip.status === 'SCHEDULED'"
        class="simulation-ready"
        role="status"
      >
        <MapPin :size="17" />
        <span>
          <small>Đang chờ tại trạm đầu</small>
          <strong>{{ startStop?.stationName ?? 'Đang tải vị trí xuất phát…' }}</strong>
        </span>
      </div>

      <div
        v-if="run"
        class="simulation-times"
      >
        <div
          v-if="dwellTime"
          class="simulation-dwell-status"
          role="status"
        >
          Đang dừng tại trạm
          <span>
            Còn {{ dwellTime.simulatedSeconds }} giây mô phỏng · khoảng
            {{ dwellTime.realSeconds }} giây thực ở {{ run.multiplier }}×
          </span>
        </div>
        <details class="trip-traffic-details">
          <summary>Chi tiết phiên mô phỏng</summary>
          <div>
            Đồng hồ:
            <time data-testid="simulation-clock">{{ displayTripTime(run.simulatedAt) }}</time>
          </div>
          <div>
            Thời lượng:
            <span data-testid="simulation-elapsed">{{ run.elapsedSeconds.toFixed(1) }}</span> /
            {{ run.durationSeconds }} giây
          </div>
        </details>
      </div>

      <button
        class="fleet-text-button simulation-locate"
        :disabled="loading || !detail"
        @click="onShowRoute"
      >
        <MapPin :size="14" />
        Định vị xe và toàn tuyến
      </button>

      <fieldset :disabled="!canControl">
        <legend>Điều khiển phiên mô phỏng</legend>
        <div class="playback-row">
          <button
            class="play-button"
            :disabled="!canPlay"
            :aria-label="running ? 'Tạm dừng mô phỏng' : `${playLabel} mô phỏng`"
            @click="simulator.command(running ? 'pause' : 'play')"
          >
            <Pause
              v-if="running"
              :size="16"
            />
            <Play
              v-else
              :size="16"
            />
            <span>{{ playLabel }}</span>
          </button>
          <button
            v-for="speed in speeds"
            :key="speed"
            :disabled="!run || !active || (run.status !== 'RUNNING' && run.status !== 'PAUSED')"
            :aria-label="`Tốc độ ${speed}x`"
            :aria-pressed="run?.multiplier === speed"
            @click="simulator.command('speed', speed)"
          >
            {{ speed }}×
          </button>
        </div>
        <div class="simulation-actions">
          <button
            class="btn-secondary"
            :disabled="!run || !active"
            @click="confirm = 'stop'"
          >
            <Square :size="13" />Dừng &amp; hủy chuyến
          </button>
          <button
            class="btn-secondary"
            :disabled="!run"
            @click="confirm = 'reset'"
          >
            <RotateCcw :size="13" />Chạy lại
          </button>
        </div>
      </fieldset>

      <section
        v-if="orderedStops.length"
        class="simulation-stops-card"
      >
        <div class="simulation-stops-heading">
          <span>
            <small>LỘ TRÌNH QUA TRẠM</small>
            <strong>Trạng thái check-in</strong>
          </span>
          <b>{{ completedStops }}/{{ totalStops }}</b>
        </div>
        <ol class="simulation-stop-list">
          <li
            v-for="(stop, index) in orderedStops"
            :key="stop.sequenceNumber"
            :data-state="stopState(stop.sequenceNumber)"
          >
            <button
              type="button"
              :aria-label="`Xem trạm ${stop.sequenceNumber}: ${stop.stationName} trên bản đồ`"
              @click="onShowStop?.(stop.sequenceNumber)"
            >
              <span class="simulation-stop-marker">
                <Check
                  v-if="stopState(stop.sequenceNumber) === 'checked-in'"
                  :size="13"
                />
                <span v-else>{{ stop.sequenceNumber }}</span>
              </span>
              <span class="simulation-stop-copy">
                <strong>{{ stop.stationName }}</strong>
                <small>
                  {{ stopRoleLabel(index) }}
                  <template v-if="visitByStop.get(stop.sequenceNumber)">
                    · Check-in
                    {{
                      displayTripTime(visitByStop.get(stop.sequenceNumber)?.actualArrivalAt ?? null)
                    }}
                  </template>
                  <template v-else-if="stopState(stop.sequenceNumber) === 'next'">
                    · Xe đang hướng tới trạm
                  </template>
                  <template v-else> · Vùng nhận diện {{ stop.checkinRadiusMeters }} m </template>
                </small>
              </span>
              <span class="simulation-stop-status">
                {{ stopStatusLabel(stop.sequenceNumber, index) }}
              </span>
            </button>
          </li>
        </ol>
      </section>

      <p
        v-if="run?.traffic?.blocked"
        class="simulation-error"
        role="status"
      >
        Đường phía trước bị chặn, chưa xác định thời gian đến.
      </p>
    </template>

    <FleetConfirmDialog
      v-if="confirm"
      :title="confirm === 'stop' ? 'Dừng mô phỏng và hủy chuyến?' : 'Chạy lại chuyến này từ đầu?'"
      :message="
        confirm === 'stop'
          ? 'Xe dừng mô phỏng và chuyến hiện tại được hủy. Lịch sử vị trí vẫn được lưu.'
          : 'Giữ nguyên mã chuyến và tuyến. Xe trở về trạm đầu, chờ bạn bấm Bắt đầu. Lịch sử và check-in được lưu riêng theo từng lần chạy.'
      "
      :confirm-label="confirm === 'stop' ? 'Xác nhận dừng chuyến' : 'Đặt lại chuyến'"
      :busy="busy"
      :on-close="
        () => {
          confirm = null;
        }
      "
      :on-confirm="confirmCommand"
    />
  </section>
</template>
