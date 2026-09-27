<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  BusFront,
  CircleCheck,
  Clock3,
  IdCard,
  MapPin,
  Milestone,
  Phone,
  Play,
  RotateCcw,
  Route as RouteIcon,
  UserRound,
} from '@lucide/vue';
import { TRIP_STATUS_LABELS, type TripSummary } from '@/features/fleet/types/fleet';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import type { RouteDetail, RouteStop } from '@/features/routes/types/route';
import { formatDuration } from '@/shared/utils/format';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';

const props = withDefaults(
  defineProps<{
    trip: TripSummary | null;
    route?: RouteDetail | null;
    visitedStopSequences?: number[];
    simulationBusy: boolean;
    simulationDisabledReason: string | null;
    simulationReplayAvailable: boolean;
    simulationReplayDisabledReason: string | null;
    onStartSimulation: (tripId: number) => void;
    onReplaySimulation: (tripId: number) => Promise<boolean>;
    onClear: () => void;
  }>(),
  { route: null, visitedStopSequences: () => [] },
);

const replayConfirm = ref(false);
const routeStops = computed(() =>
  [...(props.route?.stops ?? [])].sort((left, right) => left.sequenceNumber - right.sequenceNumber),
);
const visitedStops = computed(() => new Set(props.visitedStopSequences));
const nextStopSequence = computed(
  () =>
    routeStops.value.find((stop) => !visitedStops.value.has(stop.sequenceNumber))?.sequenceNumber,
);

function stopState(stop: RouteStop) {
  if (props.trip?.status === 'COMPLETED' || visitedStops.value.has(stop.sequenceNumber))
    return 'done';
  if (props.trip?.status === 'IN_PROGRESS' && stop.sequenceNumber === nextStopSequence.value)
    return 'current';
  return 'upcoming';
}

function stopRole(stop: RouteStop) {
  if (stop.role === 'START') return 'Điểm xuất phát';
  if (stop.role === 'END') return 'Điểm kết thúc';
  return 'Trạm dừng';
}

async function replaySimulation() {
  if (!props.trip) return;
  const replayed = await props.onReplaySimulation(props.trip.id);
  if (replayed) replayConfirm.value = false;
}
</script>

<template>
  <div class="tracking-vehicle-card">
    <template v-if="trip">
      <header class="tracking-vehicle-summary">
        <div class="tracking-vehicle-summary-top">
          <span class="tracking-vehicle-icon"><BusFront :size="19" /></span>
          <span class="tracking-vehicle-status">{{ TRIP_STATUS_LABELS[trip.status] }}</span>
        </div>
        <h2>{{ trip.vehiclePlateNumber }}</h2>
        <p class="tracking-vehicle-route"><RouteIcon :size="14" />{{ trip.routeName }}</p>
        <p
          v-if="trip.dispatchMode === 'FIXED_SCHEDULE'"
          class="tracking-vehicle-schedule"
        >
          <Clock3 :size="13" />Khởi hành theo lịch {{ displayTripTime(trip.scheduledDepartureAt) }}
        </p>
      </header>

      <section
        class="tracking-route-summary"
        aria-labelledby="tracking-route-title"
      >
        <div class="tracking-section-heading">
          <div>
            <span>LỊCH TRÌNH TUYẾN ĐƯỜNG</span>
            <h3 id="tracking-route-title">Các điểm xe đi qua</h3>
          </div>
          <span class="tracking-stop-count">{{ routeStops.length || '—' }} trạm</span>
        </div>
        <div
          v-if="route"
          class="tracking-route-metrics"
        >
          <span
            ><Milestone :size="13" />{{ (route.totalDistanceMeters / 1000).toFixed(1) }} km</span
          >
          <span><Clock3 :size="13" />{{ formatDuration(route.estimatedTripDurationSeconds) }}</span>
        </div>
        <div
          v-if="routeStops.length"
          class="tracking-route-timeline"
        >
          <div
            v-for="stop in routeStops"
            :key="`${stop.sequenceNumber}:${stop.stationId}`"
            :class="['tracking-route-stop', stopState(stop)]"
          >
            <span class="tracking-route-node">
              <CircleCheck
                v-if="stopState(stop) === 'done'"
                :size="13"
              />
              <span v-else>{{ stop.sequenceNumber }}</span>
            </span>
            <div>
              <strong>{{ stop.stationName }}</strong>
              <small>{{ stopRole(stop) }}</small>
            </div>
            <span
              v-if="stopState(stop) === 'current'"
              class="tracking-route-current"
              >Sắp tới</span
            >
          </div>
        </div>
        <p
          v-else-if="route"
          class="tracking-route-loading"
        >
          Tuyến chưa có điểm dừng để hiển thị.
        </p>
        <p
          v-else
          class="tracking-route-loading"
        >
          Đang tải lịch trình của tuyến…
        </p>
      </section>

      <section class="tracking-driver-summary">
        <div class="tracking-section-heading">
          <div>
            <span>TÀI XẾ PHỤ TRÁCH</span>
            <h3>Thông tin tài xế</h3>
          </div>
          <span class="tracking-driver-icon"><UserRound :size="17" /></span>
        </div>
        <div
          v-if="trip.driver"
          class="tracking-driver-content"
        >
          <strong>{{ trip.driver.fullName }}</strong>
          <div>
            <span><IdCard :size="13" />GPLX {{ trip.driver.licenseNumber }}</span>
            <a :href="`tel:${trip.driver.phoneNumber}`"
              ><Phone :size="13" />{{ trip.driver.phoneNumber }}</a
            >
          </div>
        </div>
        <div
          v-else
          class="tracking-driver-empty"
        >
          <UserRound :size="17" />
          <span
            ><strong>Chưa phân công tài xế</strong
            ><small>Cần gán tài xế trước khi khởi hành.</small></span
          >
        </div>
      </section>

      <div class="tracking-vehicle-actions">
        <template v-if="trip.status === 'SCHEDULED'">
          <button
            type="button"
            class="tracking-vehicle-start"
            :disabled="simulationBusy || !!simulationDisabledReason"
            @click="onStartSimulation(trip.id)"
          >
            <Play
              :size="16"
              aria-hidden="true"
            />
            {{ simulationBusy ? 'Đang bắt đầu…' : 'Bắt đầu mô phỏng' }}
          </button>
          <p
            v-if="simulationDisabledReason"
            class="tracking-vehicle-start-note"
            role="status"
          >
            {{ simulationDisabledReason }}
          </p>
        </template>
        <template v-else-if="simulationReplayAvailable">
          <button
            type="button"
            class="tracking-vehicle-start tracking-vehicle-replay"
            :disabled="simulationBusy || !!simulationReplayDisabledReason"
            @click="replayConfirm = true"
          >
            <RotateCcw
              :size="16"
              aria-hidden="true"
            />
            {{ simulationBusy ? 'Đang chuẩn bị…' : 'Mô phỏng lại' }}
          </button>
          <p
            v-if="simulationReplayDisabledReason"
            class="tracking-vehicle-start-note"
            role="status"
          >
            {{ simulationReplayDisabledReason }}
          </p>
        </template>
        <div class="tracking-vehicle-secondary-actions">
          <RouterLink
            :to="{ path: '/trips', query: { vehicleId: String(trip.vehicleId) } }"
            class="tracking-vehicle-link"
            ><MapPin :size="14" />Mở quản lý chuyến đi</RouterLink
          >
          <button
            type="button"
            class="tracking-vehicle-clear"
            @click="onClear"
          >
            Bỏ chọn xe
          </button>
        </div>
      </div>
    </template>
    <div
      v-else
      class="tracking-vehicle-empty"
      role="status"
    >
      <BusFront :size="22" />
      <span>
        <strong>Chưa tải được chuyến</strong>
        <small>Hãy chọn lại xe trên bản đồ.</small>
      </span>
    </div>
    <FleetConfirmDialog
      v-if="replayConfirm && trip"
      title="Mô phỏng lại từ trạm đầu?"
      :message="`Xe ${trip.vehiclePlateNumber} sẽ được chuẩn bị cho một lượt mô phỏng mới của chuyến #${trip.id}.`"
      confirm-label="Chuẩn bị lượt chạy mới"
      variant="replay"
      :busy="simulationBusy"
      :confirm-disabled="!!simulationReplayDisabledReason"
      :on-close="
        () => {
          replayConfirm = false;
        }
      "
      :on-confirm="replaySimulation"
    >
      <div class="replay-confirm-impact">
        <div class="replay-confirm-impact-item retained">
          <span class="replay-confirm-impact-icon"><CircleCheck :size="16" /></span>
          <span>
            <strong>Lưu lượt chạy hiện tại</strong>
            <small>Vị trí, check-in và lịch sử đã ghi nhận vẫn được giữ lại.</small>
          </span>
        </div>
        <div class="replay-confirm-impact-item reset">
          <span class="replay-confirm-impact-icon"><MapPin :size="16" /></span>
          <span>
            <strong>Đưa xe về trạm đầu</strong>
            <small>Tiến độ của lượt mô phỏng mới bắt đầu lại từ đầu tuyến.</small>
          </span>
        </div>
        <div class="replay-confirm-impact-item ready">
          <span class="replay-confirm-impact-icon"><Play :size="16" /></span>
          <span>
            <strong>Chờ lệnh khởi hành</strong>
            <small>Xe chỉ di chuyển sau khi bạn bấm “Bắt đầu” trong bảng mô phỏng.</small>
          </span>
        </div>
      </div>
    </FleetConfirmDialog>
  </div>
</template>
