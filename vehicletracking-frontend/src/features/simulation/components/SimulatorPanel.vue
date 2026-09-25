<script setup lang="ts">
import { computed, defineAsyncComponent, defineComponent, h, ref } from 'vue';
import { Car, Pause, Play, RotateCcw, Square } from '@lucide/vue';
import type { useSimulator } from '@/features/simulation/composables/useSimulator';
import type { useSimulationFleet } from '@/features/simulation/composables/useSimulationFleet';
import type { OperationsSnapshot, SimulationStatus, StreamConnection } from '@/features/tracking/types/operations';
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
}>();
const confirm = ref<'stop' | 'reset' | null>(null);
const trip = computed(() => props.simulator.trip),
  run = computed(() => props.simulator.run),
  detail = computed(() => props.simulator.detail);
const busy = computed(() => props.simulator.busy),
  loading = computed(() => props.simulator.loading);
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
const frame = computed(() => run.value?.frame),
  options = computed(() => props.snapshot?.trips ?? []);
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
  if (run.value) {
    return SIMULATION_LABELS[run.value.status as SimulationStatus] ?? run.value.status;
  }
  return active.value ? 'Sẵn sàng' : 'Chuyến đã kết thúc';
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
      <span>{{ connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · đang thử lại' }}</span
      ><button
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
    <label class="simulation-select"
      >Chuyến mô phỏng<select
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
      </select></label
    >
    <div
      v-if="!trip && !loading"
      class="telemetry-placeholder"
    >
      <Car :size="26" />
      <div>
        <strong>Chọn xe để điều khiển</strong
        ><span>Xe chờ nằm ở trạm đầu. Bấm từng xe để bắt đầu, tạm dừng hoặc đổi tốc độ phát.</span>
      </div>
    </div>
    <p
      v-if="loading"
      class="availability-note"
      role="status"
    >
      Đang tải tuyến mô phỏng…
    </p>
    <div
      v-if="trip"
      class="simulation-summary"
    >
      <strong>#{{ trip.id }} · {{ trip.vehiclePlateNumber }}</strong
      ><span data-testid="simulation-status">{{ simulationStatusLabel }}</span>
    </div>
    <p
      v-if="trip"
      class="availability-note"
    >
      {{
        usingGps
          ? 'Xe đang được theo dõi bằng GPS, không chạy mô phỏng.'
          : 'Chế độ mô phỏng · Không phải hành trình thực tế'
      }}
    </p>
    <template v-if="trip"
      ><template v-if="!run && trip.status === 'SCHEDULED'"
        ><div
          v-if="detail?.stops[0]"
          class="simulation-ready"
          role="status"
        >
          <strong>Chờ xuất phát</strong>
          <p>Trạm đầu: {{ detail.stops[0].stationName }}</p>
        </div>
        <p
          v-else
          role="status"
        >
          Đang tải trạm đầu của xe…
        </p></template
      ><button
        class="fleet-text-button simulation-locate"
        :disabled="loading || !detail"
        @click="onShowRoute"
      >
        Xem vị trí xe và tuyến đang chạy
      </button></template
    >
    <fieldset :disabled="!canControl">
      <legend>Điều khiển mô phỏng · tốc độ phát</legend>
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
          /><Play
            v-else
            :size="16"
          /><span>{{ playLabel }}</span>
        </button>
        <button
          v-for="speed in speeds"
          :key="speed"
          :disabled="!run || !active || (run.status !== 'RUNNING' && run.status !== 'PAUSED')"
          :aria-label="`Tốc độ ${speed}x`"
          :aria-pressed="run?.multiplier === speed"
          @click="simulator.command('speed', speed)"
        >
          {{ speed }}x
        </button>
      </div>
      <div class="simulation-actions">
        <button
          class="btn-secondary"
          :disabled="!run || !active"
          @click="confirm = 'stop'"
        >
          <Square :size="13" />Dừng &amp; hủy chuyến</button
        ><button
          class="btn-secondary"
          :disabled="!run"
          @click="confirm = 'reset'"
        >
          <RotateCcw :size="13" />Chạy lại
        </button>
      </div>
    </fieldset>
    <p
      v-if="trip"
      class="availability-note"
    >
      1× / 5× / 10× là tốc độ phát, không phải vận tốc xe.
    </p>
    <p
      v-if="run?.traffic?.blocked"
      class="simulation-error"
      role="status"
    >
      Đường phía trước bị chặn, chưa xác định thời gian đến.
    </p>
    <p
      v-if="run?.traffic?.status === 'STALE' || run?.traffic?.status === 'UNAVAILABLE'"
      role="status"
    >
      Dữ liệu giao thông chưa được cập nhật.
    </p>
    <div
      v-if="run"
      class="simulation-times"
    >
      <div
        :class="{ 'simulation-dwell-status': frame?.dwelling }"
        role="status"
      >
        {{
          frame?.finished
            ? 'Đã đi hết tuyến'
            : frame?.dwelling
              ? 'Đang dừng tại trạm theo lịch tuyến'
              : 'Xe di chuyển theo tuyến của chuyến'
        }}
        <span v-if="dwellTime">
          Còn {{ dwellTime.simulatedSeconds }} giây mô phỏng · khoảng
          {{ dwellTime.realSeconds }} giây thực ở {{ run.multiplier }}×
        </span>
      </div>
      <details class="trip-traffic-details">
        <summary>Thông tin kỹ thuật</summary>
        <div>
          Đồng hồ mô phỏng:
          <time data-testid="simulation-clock">{{ displayTripTime(run.simulatedAt) }}</time>
        </div>
        <div>
          Đã chạy:
          <span data-testid="simulation-elapsed">{{ run.elapsedSeconds.toFixed(1) }}</span> /
          {{ run.durationSeconds }} giây mô phỏng
        </div>
      </details>
    </div>
    <div class="trip-progress">
      <span
        >Tiến độ tuyến <b>{{ frame ? `${Math.round(frame.progressPercent)}%` : '—' }}</b></span
      >
      <div
        class="progress-track"
        role="progressbar"
        aria-label="Tiến độ tuyến mô phỏng"
        :aria-valuenow="frame?.progressPercent ?? 0"
        :aria-valuemin="0"
        :aria-valuemax="100"
      >
        <span :style="{ width: `${frame?.progressPercent ?? 0}%` }" />
      </div>
    </div>
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
