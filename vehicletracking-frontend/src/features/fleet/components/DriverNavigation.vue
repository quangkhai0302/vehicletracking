<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue';
import {
  ArrowLeft,
  Navigation,
  Play,
  Route,
  RefreshCw,
  WifiOff,
  X,
  Check,
  MapPin,
  Gauge,
  Clock3,
  ChevronDown,
  AlertTriangle,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import { useDriverNavigation } from '@/features/fleet/composables/useDriverNavigation';
import DriverNavigationMap from './DriverNavigationMap.vue';
import SimulationIncidentDialog from '@/features/simulation/components/SimulationIncidentDialog.vue';
import { TRIP_STATUS_LABELS, vehicleTypeLabel } from '@/features/fleet/types/fleet';
import {
  driverNavigationPresentation,
  formatSimulationDuration,
} from '@/features/fleet/utils/driverNavigationPresentation';
import { stopRoleLabel } from '@/features/map/utils/routeStopPresentation';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import { SIMULATION_LABELS } from '@/features/tracking/types/operations';
import { formatDuration } from '@/shared/utils/format';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import '@/features/fleet/styles/driver-navigation.css';

const props = defineProps<{ tripId: number }>();
const navigation = useDriverNavigation(toRef(props, 'tripId'));
useErrorToast(() => navigation.error);
const incidentDialog = ref(false);
const incidentIdempotencyKey = ref('');
const canReportIncident = computed(() => {
  const snapshot = navigation.snapshot;
  const run = snapshot?.simulation;
  return !!snapshot && snapshot.trip.status === 'IN_PROGRESS' &&
    (run?.status === 'RUNNING' || run?.status === 'PAUSED') &&
    navigation.connected && !navigation.busy;
});
function openIncidentDialog() {
  incidentIdempotencyKey.value = crypto.randomUUID();
  incidentDialog.value = true;
}
async function submitIncident(input: { type: 'VEHICLE_BREAKDOWN' | 'EMERGENCY_STOP' | 'ROAD_BLOCKED' | 'OTHER'; severity: 'MAJOR' | 'CRITICAL'; detail: string }) {
  return navigation.reportIncident({ ...input, idempotencyKey: incidentIdempotencyKey.value });
}
const mapView = ref<InstanceType<typeof DriverNavigationMap> | null>(null);
const navigationView = ref<HTMLElement | null>(null);
const tripDetails = ref<HTMLElement | null>(null);
function scrollToMap() {
  navigationView.value?.scrollTo?.({ top: 0 });
}
function showTripDetails() {
  tripDetails.value?.scrollIntoView?.({ block: 'start' });
}
function showStop(sequence: number) {
  mapView.value?.showStop(sequence);
  scrollToMap();
}
watch(
  () => navigation.snapshot?.trip.status,
  (status, previous) => {
    if (status === 'IN_PROGRESS' && previous === 'SCHEDULED') scrollToMap();
  },
);
const info = computed(() =>
  navigation.snapshot ? driverNavigationPresentation(navigation.snapshot) : null,
);
const nextStop = computed(() => info.value?.nextStop);
const guidanceText = computed(() => {
  if (navigation.snapshot?.trip.status === 'COMPLETED') return 'Đã hoàn thành chuyến';
  if (navigation.snapshot?.trip.status === 'CANCELLED') return 'Chuyến đã bị hủy';
  if (navigation.snapshot?.simulation?.frame?.dwelling)
    return `Dừng tại ${nextStop.value?.stationName ?? 'trạm'}`;
  const maneuver = navigation.snapshot?.guidance?.maneuver;
  if (maneuver?.instruction) return maneuver.instruction;
  if (maneuver?.direction?.includes('left')) return 'Rẽ trái theo lộ trình';
  if (maneuver?.direction?.includes('right')) return 'Rẽ phải theo lộ trình';
  if (maneuver?.action === 'arrive') return `Đến ${nextStop.value?.stationName ?? 'trạm'}`;
  return navigation.snapshot?.trip.status === 'SCHEDULED'
    ? 'Sẵn sàng khởi hành'
    : `Theo lộ trình đến ${nextStop.value?.stationName ?? 'trạm tiếp theo'}`;
});
const hasInstructions = computed(() =>
  navigation.snapshot?.route.sections.some((s) => (s.instructions?.length ?? 0) > 0),
);
const distance = (meters: number) =>
  meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`;
const boardingCount = ref('');
const onboardCount = computed(() => (navigation.snapshot?.checkIns.visits ?? [])
  .reduce((total, visit) => total + (visit.employeeBoardingCount ?? 0), 0));
const afterBoardingCount = computed(() => /^\d+$/.test(boardingCount.value)
  ? onboardCount.value + Number(boardingCount.value) : null);
const exceedsCapacity = computed(() => {
  const capacity = navigation.snapshot?.trip.seatCapacity;
  return capacity != null && afterBoardingCount.value != null && afterBoardingCount.value > capacity;
});
const boardingVisit = computed(() => {
  const snapshot = navigation.snapshot;
  if (!snapshot || snapshot.trip.status !== 'IN_PROGRESS') return null;
  const terminal = Math.max(...snapshot.stops.map((stop) => stop.sequenceNumber));
  return snapshot.checkIns.visits.find((visit) => visit.stopSequence < terminal && visit.employeeBoardingCount == null) ?? null;
});
watch(() => `${boardingVisit.value?.attemptNumber ?? ''}:${boardingVisit.value?.stopSequence ?? ''}`,
  () => {
    const visit = boardingVisit.value;
    const stop = visit && navigation.snapshot?.stops.find((item) => item.sequenceNumber === visit.stopSequence);
    boardingCount.value = stop?.expectedEmployeeBoardingCount == null ? '' : String(stop.expectedEmployeeBoardingCount);
  }, { immediate: true });
async function saveBoardingCount() {
  const visit = boardingVisit.value;
  if (!visit || !/^\d+$/.test(boardingCount.value) || exceedsCapacity.value) return;
  await navigation.confirmBoarding(visit.stopSequence, Number(boardingCount.value));
}
</script>

<template>
  <main
    ref="navigationView"
    class="driver-navigation business-ui"
  >
    <header class="driver-navigation-header">
      <RouterLink
        to="/driver/today"
        aria-label="Quay lại chuyến của tôi"
        ><ArrowLeft :size="22"
      /></RouterLink>
      <div>
        <h1>{{ navigation.snapshot?.trip.routeName ?? 'Bản đồ chuyến đi' }}</h1>
        <p>
          Chuyến #{{ tripId }} · {{ navigation.snapshot?.trip.vehiclePlateNumber ?? 'Đang tải…' }}
        </p>
      </div>
      <span class="driver-simulator-badge">Mô phỏng</span>
    </header>
    <div
      v-if="navigation.loading"
      class="driver-navigation-state"
      role="status"
    >
      Đang tải bản đồ chuyến…
    </div>
    <div
      v-else-if="!navigation.snapshot"
      class="driver-navigation-state"
      role="alert"
    >
      <p>{{ navigation.error ?? 'Không tìm thấy chuyến được phân công.' }}</p>
      <button @click="navigation.retry"><RefreshCw :size="18" /> Thử lại</button>
    </div>
    <div
      v-else
      class="driver-navigation-layout"
    >
      <section class="driver-navigation-map-column">
        <DriverNavigationMap
          ref="mapView"
          :snapshot="navigation.snapshot"
          :now="navigation.now"
          :connected="navigation.connected"
          :preview="navigation.selectedOption?.sections ?? null"
        />
        <div
          class="driver-guidance"
          aria-live="polite"
        >
          <Navigation :size="27" />
          <div>
            <strong>{{ guidanceText }}</strong>
            <span v-if="navigation.snapshot.guidance"
              >Còn {{ distance(navigation.snapshot.guidance.distanceMeters) }}</span
            >
            <span v-else>{{ TRIP_STATUS_LABELS[navigation.snapshot.trip.status] }}</span>
          </div>
        </div>
        <div
          v-if="!navigation.connected"
          class="driver-navigation-offline"
          role="status"
        >
          <WifiOff :size="16" /> Mất đồng bộ · Đang kết nối lại
        </div>
        <div
          v-if="navigation.selectedOption"
          class="driver-preview-badge"
        >
          Đường màu cam: đang xem thử, chưa áp dụng
        </div>
        <button
          type="button"
          class="driver-navigation-scroll-hint"
          aria-controls="driver-navigation-details"
          @click="showTripDetails"
        >
          <ChevronDown :size="18" />Kéo xuống xem thông tin chuyến
        </button>
      </section>
      <aside
        id="driver-navigation-details"
        ref="tripDetails"
        class="driver-navigation-sidebar"
        aria-label="Điều khiển chuyến đi"
      >
        <div class="driver-navigation-summary">
          <div>
            <span><MapPin :size="12" />Trạm kế tiếp</span
            ><strong>{{ nextStop?.stationName ?? 'Đã kết thúc lộ trình' }}</strong>
          </div>
          <div>
            <span><Clock3 :size="12" />Đến trạm kế tiếp</span>
            <strong>{{
              navigation.snapshot.simulation?.frame?.finished
                ? 'Đã đến'
                : formatSimulationDuration(info?.nextStopEtaSeconds)
            }}</strong>
          </div>
          <div>
            <span><Gauge :size="12" />Vận tốc</span
            ><strong>{{ info?.speedKmh == null ? '—' : `${info.speedKmh} km/h` }}</strong>
          </div>
          <div>
            <span><Clock3 :size="12" />Thời gian còn lại</span
            ><strong>{{
              info?.remainingSeconds == null
                ? 'Chưa khởi hành'
                : formatSimulationDuration(info.remainingSeconds)
            }}</strong>
          </div>
          <div class="driver-trip-progress">
            <div>
              <span>Tiến độ hành trình</span
              ><strong>{{ Math.round(info?.progressPercent ?? 0) }}%</strong>
            </div>
            <div
              class="driver-trip-progress-track"
              role="progressbar"
              aria-label="Tiến độ hành trình"
              :aria-valuenow="info?.progressPercent ?? 0"
              :aria-valuemin="0"
              :aria-valuemax="100"
            >
              <i :style="{ width: `${info?.progressPercent ?? 0}%` }" />
            </div>
            <small
              >{{ info?.visits.size ?? 0 }}/{{ info?.stops.length ?? 0 }} trạm đã check-in</small
            >
          </div>
          <p
            v-if="navigation.snapshot.simulation"
            class="driver-run-status"
          >
            {{ SIMULATION_LABELS[navigation.snapshot.simulation.status] }} ·
            {{ navigation.snapshot.simulation.multiplier }}×
          </p>
          <p>
            {{ navigation.connected ? 'Đang đồng bộ với điều phối' : 'Dữ liệu chưa được cập nhật' }}
            ·
            {{
              navigation.snapshot.routeRevisionId
                ? `Lộ trình #${navigation.snapshot.routeRevisionId}`
                : 'Lộ trình ban đầu'
            }}
          </p>
          <p
            v-if="!hasInstructions"
            class="driver-guidance-missing"
          >
            Tuyến này chưa có chỉ dẫn rẽ từng bước. Bản đồ vẫn hiển thị lộ trình chính thức.
          </p>
        </div>
        <button
          v-if="canReportIncident"
          class="driver-incident-report-button"
          type="button"
          @click="openIncidentDialog"
        >
          <AlertTriangle :size="17" /> Báo cáo sự cố
        </button>
        <p
          v-if="navigation.error"
          class="driver-navigation-error"
          role="alert"
        >
          {{ navigation.error }}
        </p>
        <form v-if="boardingVisit" class="driver-boarding-confirm" @submit.prevent="saveBoardingCount">
          <span class="driver-boarding-eyebrow">ĐÃ ĐẾN ĐIỂM ĐÓN</span>
          <strong>{{ navigation.snapshot?.stops.find((stop) => stop.sequenceNumber === boardingVisit?.stopSequence)?.stationName }}</strong>
          <p>Nhập số người lên xe tại điểm này. Hành khách sẽ xuống tại điểm đến cuối.</p>
          <p class="driver-boarding-capacity" role="status">
            Đang trên xe: {{ onboardCount }}<template v-if="navigation.snapshot.trip.seatCapacity != null"> / {{ navigation.snapshot.trip.seatCapacity }} ghế</template>
            <template v-if="afterBoardingCount != null"> · Sau khi đón: {{ afterBoardingCount }}</template>
          </p>
          <label>Số người lên xe
            <input v-model="boardingCount" type="number" min="0" step="1" inputmode="numeric" required />
          </label>
          <p v-if="exceedsCapacity" class="driver-boarding-error" role="alert">Số người vượt quá số ghế còn trống.</p>
          <button class="driver-navigation-primary" type="submit" :disabled="navigation.busy || !navigation.connected || !/^\d+$/.test(boardingCount) || exceedsCapacity">
            {{ navigation.busy ? 'Đang lưu…' : 'Xác nhận số người' }}
          </button>
        </form>
        <button
          v-if="navigation.snapshot.trip.status === 'SCHEDULED'"
          class="driver-navigation-primary"
          :disabled="navigation.busy || !navigation.connected"
          @click="navigation.start"
        >
          <Play :size="18" />{{ navigation.busy ? 'Đang khởi hành…' : 'Bắt đầu chuyến' }}
        </button>
        <button
          v-else-if="!navigation.options && navigation.snapshot.trip.status === 'IN_PROGRESS'"
          class="driver-navigation-primary"
          :disabled="!navigation.canChange"
          @click="navigation.loadOptions"
        >
          <Route :size="18" />{{ navigation.busy ? 'Đang tìm đường…' : 'Đổi đường' }}
        </button>
        <section
          v-if="navigation.options"
          class="driver-route-options"
          aria-label="Chọn đường thay thế"
        >
          <header>
            <h2>Chọn đường đi</h2>
            <button
              :disabled="navigation.busy"
              aria-label="Đóng phương án đường"
              @click="navigation.cancelOptions"
            >
              <X :size="20" />
            </button>
          </header>
          <p v-if="navigation.options.options.length === 0">
            Không có phương án khả dụng tại vị trí hiện tại.
          </p>
          <p v-else-if="navigation.options.options.length === 1">
            HERE chỉ tìm được một phương án khả dụng.
          </p>
          <div class="driver-route-option-list">
            <label
              v-for="option in navigation.options.options"
              :key="option.optionIndex"
              :class="{ selected: navigation.selectedIndex === option.optionIndex }"
            >
              <input
                v-model="navigation.selectedIndex"
                type="radio"
                name="driver-route-option"
                :value="option.optionIndex"
                :disabled="navigation.busy"
              />
              <span
                ><strong>{{ option.label }}</strong
                ><small
                  >{{ distance(option.distanceMeters) }} ·
                  {{ formatDuration(option.durationSeconds) }}</small
                ></span
              >
            </label>
          </div>
          <p>Giữ nguyên các trạm còn lại. Admin nhận thông báo sau khi bạn xác nhận.</p>
          <p
            v-if="navigation.optionsExpired"
            class="driver-navigation-error"
          >
            Phương án đã hết hạn. Hãy tìm đường mới.
          </p>
          <button
            class="driver-navigation-primary"
            :disabled="
              !navigation.canChange ||
              navigation.optionsExpired ||
              navigation.selectedIndex === null
            "
            @click="navigation.apply"
          >
            {{ navigation.busy ? 'Đang áp dụng…' : 'Xác nhận đổi đường' }}
          </button>
          <button
            class="driver-navigation-secondary"
            :disabled="navigation.busy || !navigation.connected"
            @click="navigation.loadOptions"
          >
            <RefreshCw :size="16" />Tìm đường mới
          </button>
        </section>
        <details
          v-if="info"
          class="driver-trip-stops"
        >
          <summary>
            Các trạm của chuyến <span>{{ info.stops.length }} trạm</span>
          </summary>
          <p class="driver-trip-identity">
            {{ vehicleTypeLabel(navigation.snapshot.trip.vehicleType) }} ·
            {{ navigation.snapshot.trip.vehiclePlateNumber }}<br />{{
              distance(navigation.snapshot.route.totalDistanceMeters)
            }}
            · {{ formatDuration(navigation.snapshot.route.estimatedTripDurationSeconds) }} toàn
            tuyến
          </p>
          <ol>
            <li
              v-for="(stop, index) in info.stops"
              :key="stop.sequenceNumber"
              :data-state="info.state(stop.sequenceNumber)"
            >
              <button
                :aria-label="`Xem trạm ${stop.sequenceNumber}: ${stop.stationName}`"
                @click="showStop(stop.sequenceNumber)"
              >
                <span class="driver-trip-stop-number"
                  ><Check
                    v-if="info.state(stop.sequenceNumber) === 'checked-in'"
                    :size="16"
                  /><template v-else>{{ stop.sequenceNumber }}</template></span
                >
                <span class="driver-trip-stop-copy"
                  ><strong>{{ stop.stationName }}</strong
                  ><small
                    >{{ stopRoleLabel(info.role(index)) }} · Dừng
                    {{ stop.dwellDurationSeconds }} giây</small
                  >
                  <small v-if="info.visits.has(stop.sequenceNumber)"
                    >Check-in
                    {{
                      displayTripTime(info.visits.get(stop.sequenceNumber)!.actualArrivalAt)
                    }}</small
                  >
                </span>
                <span class="driver-trip-stop-state">{{
                  info.statusLabel(stop.sequenceNumber, index)
                }}</span>
              </button>
            </li>
          </ol>
          <p class="driver-trip-identity">Chọn một trạm để mở vị trí và thông tin trên bản đồ.</p>
        </details>
        <p class="driver-navigation-demo">
          Vị trí do simulator tạo, không phải GPS thực tế. Đóng trang không dừng chuyến.
        </p>
      </aside>
    </div>
    <SimulationIncidentDialog
      v-if="incidentDialog"
      :busy="navigation.busy"
      :error="navigation.error"
      :on-submit="submitIncident"
      :on-close="() => { incidentDialog = false }"
    />
  </main>
</template>
