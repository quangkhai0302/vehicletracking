<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  ArrowLeft,
  BusFront,
  Check,
  Clock3,
  MapPin,
  Play,
  RefreshCw,
  Route as RouteIcon,
  Trash2,
  UserRound,
  X,
} from '@lucide/vue';
import {
  TRIP_STATUS_LABELS,
  type Driver,
  type FleetVehicle,
  type TripAction,
  type TripDetail,
  type TripStop,
} from '@/features/fleet/types/fleet';
import { displayTripTime, scheduledStopArrivalAt } from '@/features/fleet/utils/tripTime';
import { formatDuration } from '@/shared/utils/format';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
import { useTripCheckIns } from '@/features/fleet/composables/useTripCheckIns';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { StopVisit } from '@/features/fleet/types/checkin';
import { useTripEta } from '@/features/fleet/composables/useTripEta';
import { trafficSourceLabel } from '@/features/traffic/utils/tripTraffic';
import RouteRevisionPanel from './RouteRevisionPanel.vue';
import AdminDispatchPanel from '@/features/dispatch/components/AdminDispatchPanel.vue';
import { DISPATCH_STATE_LABELS } from '@/features/dispatch/types/dispatch';
import { useErrorToast } from '@/shared/composables/useErrorToast';
const props = defineProps<{
  detail: TripDetail | null;
  loading: boolean;
  busy: boolean;
  drivers: Driver[];
  vehicles: FleetVehicle[];
  onClose: () => void;
  onRetry: () => void;
  onAction: (action: TripAction, reason?: string) => Promise<boolean>;
  onUpdateDriver?: (id: number, driverId: number | null) => Promise<boolean>;
  onUpdateVehicle?: (id: number, vehicleId: number) => Promise<boolean>;
  onDeleteTrip?: (id: number) => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onSimulate?: (id: number) => void;
  onViewRoute?: (id: number) => void;
  liveSnapshot?: OperationsSnapshot | null;
}>();
const confirm = ref<'complete' | 'cancel' | null>(null),
  cancelReason = ref(''),
  driverId = ref(''),
  vehicleId = ref(''),
  confirmDelete = ref(false),
  confirmDriverChange = ref(false);
const trip = computed(() => props.detail?.trip);
const assignmentRequest = computed(() => trip.value?.assignmentRequest ?? null);
const hasPendingAssignment = computed(() => assignmentRequest.value?.status === 'PENDING');
const selectedDriver = computed(() =>
  props.drivers.find((driver) => driver.id === Number(driverId.value)) ?? null,
);
const availableVehicles = computed(() =>
  props.vehicles.filter((item) => item.active || item.id === trip.value?.vehicleId),
);
const availableDrivers = computed(() =>
  props.drivers.filter((item) => item.active || item.id === trip.value?.driver?.id),
);
const hasCurrentVehicle = computed(() =>
  props.vehicles.some((item) => item.id === trip.value?.vehicleId),
);
const hasCurrentDriver = computed(() =>
  props.drivers.some((item) => item.id === trip.value?.driver?.id),
);
watch(
  [() => trip.value?.id, () => trip.value?.vehicleId, () => trip.value?.driver?.id],
  ([, currentVehicleId, currentDriverId]) => {
    vehicleId.value = currentVehicleId === undefined ? '' : String(currentVehicleId);
    driverId.value = currentDriverId === undefined ? '' : String(currentDriverId);
  },
  { immediate: true },
);
const isFixedSchedule = computed(() => trip.value?.dispatchMode === 'FIXED_SCHEDULE');
const checkins = useTripCheckIns(
  () => trip.value?.id ?? null,
  () => props.liveSnapshot ?? null,
);
const eta = useTripEta(() => trip.value?.id ?? null);
useErrorToast(() => checkins.error);
useErrorToast(() => eta.error);
const visitByStop = computed(
  () => new Map(checkins.data?.visits.map((visit) => [visit.stopSequence, visit]) ?? []),
);
const etaByStop = computed(
  () => new Map(eta.data?.stops.map((stop) => [stop.sequenceNumber, stop]) ?? []),
);
const completedStopCount = computed(
  () =>
    props.detail?.stops.filter((stop) => visitByStop.value.has(stop.sequenceNumber)).length ?? 0,
);
const checkInProgress = computed(() => {
  const total = props.detail?.stops.length ?? 0;
  return total ? Math.round((completedStopCount.value / total) * 100) : 0;
});
const canComplete = computed(() => {
  const stops = props.detail?.stops;
  const final = stops?.[stops.length - 1];
  return (
    trip.value?.status === 'IN_PROGRESS' && !!final && visitByStop.value.has(final.sequenceNumber)
  );
});
const canStart = computed(
  () =>
    trip.value?.status === 'SCHEDULED' &&
    trip.value?.dispatch?.startMode !== 'AUTO_IF_READY' &&
    !!trip.value.driver &&
    props.drivers.some((driver) => driver.id === trip.value?.driver?.id && driver.active),
);
const etaLabel = computed(() => {
  if (isFixedSchedule.value && eta.data?.source === 'ROUTE_SNAPSHOT')
    return 'Dự kiến đến (theo lịch)';
  if (eta.data?.source === 'HERE_LIVE' && eta.data.status !== 'STALE')
    return 'Dự kiến đến (theo giao thông)';
  if (eta.data?.source === 'HERE_LAST_KNOWN' || eta.data?.status === 'STALE')
    return 'Dự kiến đến (dữ liệu gần nhất)';
  return isFixedSchedule.value ? 'Dự kiến đến (theo lịch)' : 'Dự kiến đến (theo tuyến)';
});
async function saveDriver() {
  if (
    trip.value &&
    props.onUpdateDriver &&
    (driverId.value !== (trip.value.driver ? String(trip.value.driver.id) : '') || hasPendingAssignment.value)
  ) {
    if (trip.value.dispatchMode === 'ON_DEMAND' && (trip.value.driver || hasPendingAssignment.value)) {
      confirmDriverChange.value = true;
      return;
    }
    await commitDriverChange();
  }
}
async function commitDriverChange() {
  if (!trip.value || !props.onUpdateDriver) return;
  const success = await props.onUpdateDriver(trip.value.id, driverId.value ? Number(driverId.value) : null);
  if (success) confirmDriverChange.value = false;
}
async function saveVehicle() {
  if (
    trip.value &&
    props.onUpdateVehicle &&
    vehicleId.value &&
    Number(vehicleId.value) !== trip.value.vehicleId
  ) {
    await props.onUpdateVehicle(trip.value.id, Number(vehicleId.value));
  }
}
async function confirmAction() {
  if (
    confirm.value &&
    (await props.onAction(
      confirm.value,
      confirm.value === 'cancel' ? cancelReason.value : undefined,
    ))
  )
    confirm.value = null;
}
async function deleteTrip() {
  if (trip.value && props.onDeleteTrip && (await props.onDeleteTrip(trip.value.id)))
    confirmDelete.value = false;
}
const actualVisitTime = (visit: StopVisit) => displayTripTime(visit.actualArrivalAt);
const simulatedVisitTime = (visit: StopVisit) => displayTripTime(visit.simulatedArrivalAt);
function fallbackStopEta(stop: TripStop) {
  if (isFixedSchedule.value && trip.value)
    return displayTripTime(scheduledStopArrivalAt(trip.value.scheduledDepartureAt, stop.arrivalOffsetSeconds));
  if (!trip.value?.startedAt)
    return `Sau ${formatDuration(stop.arrivalOffsetSeconds)} từ lúc khởi hành`;
  return displayTripTime(
    new Date(Date.parse(trip.value.startedAt) + stop.arrivalOffsetSeconds * 1000).toISOString(),
  );
}
</script>
<template>
  <section
    class="fleet-editor trip-detail-panel"
    aria-label="Chi tiết chuyến đi"
  >
    <div class="fleet-heading trip-detail-heading">
      <button
        class="fleet-icon-button"
        :disabled="busy"
        aria-label="Đóng chi tiết chuyến"
        @click="onClose"
      >
        <ArrowLeft :size="18" />
      </button>
      <div class="trip-detail-heading-copy">
        <span class="panel-eyebrow">ĐIỀU HÀNH CHUYẾN ĐI</span>
        <div class="trip-detail-heading-title">
          <h2>{{ trip ? `Chi tiết chuyến #${trip.id}` : 'Chi tiết chuyến' }}</h2>
          <span
            v-if="trip"
            :class="`trip-status ${trip.status.toLowerCase()}`"
            >{{ TRIP_STATUS_LABELS[trip.status] }}</span
          >
          <span v-if="trip?.dispatch" class="dispatch-badge">
            {{ DISPATCH_STATE_LABELS[trip.dispatch.state] }}
          </span>
        </div>
        <p v-if="trip">Theo dõi phân công, thời gian và tiến độ qua từng điểm dừng.</p>
      </div>
      <button
        class="fleet-icon-button"
        :disabled="busy || loading"
        aria-label="Tải lại chuyến"
        @click="onRetry"
      >
        <RefreshCw :size="16" />
      </button>
    </div>
    <div class="fleet-detail-body trip-detail-body">
      <p
        v-if="loading"
        role="status"
        class="fleet-loading"
      >
        Đang tải lịch trình…
      </p>
      <template v-if="!loading && trip && detail">
        <section
          class="trip-summary"
          aria-label="Tổng quan chuyến đi"
        >
          <div class="trip-summary-vehicle">
            <span class="trip-summary-icon"><BusFront :size="22" /></span>
            <div>
              <span class="trip-summary-label">Phương tiện thực hiện</span>
              <h3>{{ trip.vehiclePlateNumber }}</h3>
              <p>
                Chuyến #{{ trip.id }} ·
                {{ isFixedSchedule ? 'Theo lịch cố định' : 'Điều phối tức thời' }}
              </p>
            </div>
          </div>
          <div class="trip-summary-meta">
            <div>
              <span class="trip-summary-meta-icon"><RouteIcon :size="17" /></span>
              <span>
                <small>Tuyến đường</small>
                <strong>{{ trip.routeName }}</strong>
              </span>
            </div>
            <div>
              <span class="trip-summary-meta-icon"><UserRound :size="17" /></span>
              <span>
                <small>Tài xế phụ trách</small>
                <strong v-if="trip.driver">{{ trip.driver.fullName }}</strong>
                <strong v-else-if="assignmentRequest?.status === 'PENDING'">
                  Chờ tài xế phản hồi · {{ assignmentRequest.candidateDriverName }}
                </strong>
                <strong v-else-if="assignmentRequest?.status === 'DECLINED'">
                  {{ assignmentRequest.candidateDriverName }} đã từ chối
                </strong>
                <strong v-else-if="assignmentRequest?.status === 'CANCELLED'">
                  Yêu cầu đã hủy · {{ assignmentRequest.candidateDriverName }}
                </strong>
                <strong v-else>Chưa phân công</strong>
                <small
                  v-if="trip.driver"
                  class="trip-driver-contact"
                >
                  {{ trip.driver.licenseNumber }} · {{ trip.driver.phoneNumber }}
                </small>
              </span>
            </div>
          </div>
          <p v-if="assignmentRequest?.status === 'PENDING'" class="trip-assignment-pending">
            Đã gửi yêu cầu lúc {{ displayTripTime(assignmentRequest.requestedAt) }}. Tài xế phải
            chấp nhận trước khi chuyến được gán và có thể khởi hành.
          </p>
          <p v-if="assignmentRequest?.status === 'DECLINED'" class="trip-assignment-declined">
            Lý do từ chối: {{ assignmentRequest.responseReason || 'Không có lý do' }}. Hãy chọn tài
            xế khác để gửi yêu cầu mới.
          </p>
          <p v-if="assignmentRequest?.status === 'CANCELLED'" class="trip-assignment-declined">
            Yêu cầu nhận chuyến đã được hủy{{ assignmentRequest.responseReason ? `: ${assignmentRequest.responseReason}` : '.' }}
          </p>
        </section>

        <section
          class="trip-traffic-details"
          aria-labelledby="trip-time-heading"
        >
          <div class="trip-card-heading">
            <span><Clock3 :size="17" /></span>
            <div>
              <h3 id="trip-time-heading">Thời gian vận hành</h3>
              <p>
                {{
                  isFixedSchedule
                    ? `Kế hoạch từ ${trip.scheduleName || 'lịch chạy tự động'} và thời gian thực tế`
                    : 'Chuyến không có giờ kế hoạch; hệ thống chỉ ghi nhận thời gian thực tế'
                }}
              </p>
            </div>
          </div>
          <dl
            class="trip-times"
            :data-fixed-schedule="isFixedSchedule"
          >
            <div v-if="isFixedSchedule">
              <dt>Xuất phát kế hoạch</dt>
              <dd>{{ displayTripTime(trip.scheduledDepartureAt) }}</dd>
            </div>
            <div v-if="isFixedSchedule">
              <dt>Hoàn thành theo lịch</dt>
              <dd>{{ displayTripTime(trip.plannedEndAt) }}</dd>
            </div>
            <div v-else>
              <dt>Tạo chuyến tức thời</dt>
              <dd>{{ displayTripTime(trip.createdAt) }}</dd>
            </div>
            <div>
              <dt>Khởi hành thực tế</dt>
              <dd>{{ displayTripTime(trip.startedAt) }}</dd>
            </div>
            <div>
              <dt>{{ trip.status === 'CANCELLED' ? 'Hủy lúc' : 'Kết thúc thực tế' }}</dt>
              <dd>{{ displayTripTime(trip.endedAt) }}</dd>
            </div>
          </dl>
        </section>
        <p
          v-if="trip.status === 'CANCELLED' && trip.cancellationReason"
          class="fleet-help trip-cancellation-reason"
        >
          <strong>Lý do hủy:</strong> {{ trip.cancellationReason }}
        </p>

        <div
          v-if="trip.status === 'SCHEDULED' && (onUpdateVehicle || onUpdateDriver)"
          class="trip-assignment-grid"
          aria-label="Phân công chuyến đi"
        >
          <div
            v-if="onUpdateVehicle"
            class="trip-schedule-editor"
          >
            <label
              >Xe thực hiện<select
                v-model="vehicleId"
                aria-label="Xe thực hiện"
                :disabled="busy"
              >
                <option
                  v-if="!hasCurrentVehicle"
                  :value="String(trip.vehicleId)"
                  disabled
                >
                  {{ trip.vehiclePlateNumber }} · Xe hiện tại
                </option>
                <option
                  v-for="vehicle in availableVehicles"
                  :key="vehicle.id"
                  :value="String(vehicle.id)"
                  :disabled="!vehicle.active"
                >
                  {{ vehicle.plateNumber }} · {{ vehicle.name }}{{ vehicle.active ? '' : ' (ngừng sử dụng)' }}
                </option>
              </select></label
            >
            <p class="fleet-help">
              Chỉ đổi xe cho chuyến này. Lịch cố định và các chuyến tương lai không bị thay đổi.
            </p>
            <div class="trip-assignment-actions">
              <button
                v-if="vehicleId !== String(trip.vehicleId)"
                class="btn-secondary trip-assignment-reset"
                :disabled="busy"
                @click="vehicleId = String(trip.vehicleId)"
              >
                Đặt lại</button
              ><button
                class="btn-primary trip-assignment-save"
                :disabled="busy || !vehicleId || Number(vehicleId) === trip.vehicleId"
                @click="saveVehicle"
              >
                <BusFront :size="14" />Lưu xe
              </button>
            </div>
          </div>
          <div
            v-if="onUpdateDriver"
            class="trip-schedule-editor"
          >
            <label
              >Tài xế thực hiện<select
                v-model="driverId"
                aria-label="Tài xế thực hiện"
                :disabled="busy"
              >
                <option value="">Chưa gán tài xế</option>
                <option
                  v-if="trip.driver && !hasCurrentDriver"
                  :value="String(trip.driver.id)"
                  disabled
                >
                  {{ trip.driver.fullName }} · Tài xế hiện tại
                </option>
                <option
                  v-for="driver in availableDrivers"
                  :key="driver.id"
                  :value="String(driver.id)"
                  :disabled="!driver.active"
                >
                  {{ driver.fullName }} · {{ driver.licenseNumber }}{{ driver.active ? '' : ' (ngừng sử dụng)' }}
                </option>
              </select></label
            >
            <p class="fleet-help">
              Chỉ đổi tài xế cho chuyến này. Với chuyến tức thời, thao tác sẽ gửi yêu cầu xác nhận;
              lịch cố định và các chuyến tương lai không bị thay đổi.
            </p>
            <div class="trip-assignment-actions">
              <button
                v-if="driverId !== (trip.driver ? String(trip.driver.id) : '') || hasPendingAssignment"
                class="btn-secondary trip-assignment-reset"
                :disabled="busy"
                @click="driverId = trip.driver ? String(trip.driver.id) : ''"
              >
                Đặt lại</button
              ><button
                class="btn-primary trip-assignment-save"
                :disabled="busy || (!hasPendingAssignment && driverId === (trip.driver ? String(trip.driver.id) : ''))"
                @click="saveDriver"
              >
                <UserRound :size="14" />{{
                  hasPendingAssignment && !driverId
                    ? 'Hủy yêu cầu'
                    : trip.dispatchMode === 'ON_DEMAND' && driverId
                    ? 'Gửi yêu cầu nhận chuyến'
                    : 'Lưu tài xế'
                }}
              </button>
            </div>
          </div>
        </div>
        <AdminDispatchPanel v-if="isFixedSchedule" :trip="trip" :drivers="drivers" :on-changed="onRetry" />

        <div
          v-if="onViewRoute || onSimulate"
          class="trip-toolbar"
          aria-label="Thao tác chuyến đi"
        >
          <div class="trip-toolbar-group trip-toolbar-navigation">
            <button
              v-if="onViewRoute"
              class="fleet-text-button"
              :disabled="busy"
              @click="onViewRoute(trip.routeId)"
            >
              <MapPin :size="15" />Xem tuyến đường
            </button>
            <button
              v-if="onSimulate && !(trip.status === 'SCHEDULED' && trip.dispatch?.startMode === 'AUTO_IF_READY')"
              class="fleet-text-button trip-simulation-button"
              :disabled="busy"
              @click="onSimulate(trip.id)"
            >
              <Play :size="15" />Mở điều khiển chuyến
            </button>
          </div>
        </div>

        <div class="trip-detail-content">
          <section
            class="trip-itinerary"
            aria-labelledby="trip-itinerary-heading"
          >
            <div class="trip-itinerary-heading">
              <div>
                <span class="trip-summary-label">Hành trình</span>
                <h3 id="trip-itinerary-heading">Các điểm dừng trên tuyến</h3>
                <p>Chọn một trạm để xem vị trí trên bản đồ giám sát.</p>
              </div>
              <strong>{{ completedStopCount }}/{{ detail.stops.length }} trạm</strong>
            </div>
            <ol class="trip-timeline">
              <li
                v-for="(stop, index) in detail.stops"
                :key="stop.sequenceNumber"
                :data-visited="visitByStop.has(stop.sequenceNumber)"
              >
                <span
                  :class="`stop-order ${index === 0 ? 'start' : index === detail.stops.length - 1 ? 'end' : 'stop'}`"
                  >{{ stop.sequenceNumber }}</span
                >
                <div class="trip-stop-card">
                  <div class="trip-stop-heading">
                    <button
                      class="fleet-stop-link"
                      @click="onFocusStop([stop.latitude, stop.longitude], 16)"
                    >
                      <span>{{ stop.stationName }}</span
                      ><MapPin :size="14" />
                    </button>
                    <span class="fleet-help">{{
                      index === 0
                        ? 'Điểm đầu'
                        : index === detail.stops.length - 1
                          ? 'Điểm cuối'
                          : 'Trạm dừng'
                    }}</span>
                  </div>
                  <div class="trip-stop-status">
                    <span
                      v-if="!visitByStop.has(stop.sequenceNumber)"
                      class="trip-eta-stop"
                      >{{ etaLabel }}:
                      {{
                        etaByStop.get(stop.sequenceNumber)?.etaAt &&
                        (!isFixedSchedule || eta.data?.source !== 'ROUTE_SNAPSHOT')
                          ? displayTripTime(etaByStop.get(stop.sequenceNumber)!.etaAt)
                          : eta.data?.status === 'BLOCKED'
                            ? 'Đường bị đóng'
                            : fallbackStopEta(stop)
                      }}</span
                    ><span
                      v-if="visitByStop.has(stop.sequenceNumber)"
                      class="trip-checkin-done"
                      ><Check :size="14" />Ghi nhận thực tế lúc
                      {{ actualVisitTime(visitByStop.get(stop.sequenceNumber)!) }}</span
                    ><span
                      v-if="
                        visitByStop.get(stop.sequenceNumber)?.source === 'SIMULATOR' &&
                        visitByStop.get(stop.sequenceNumber)?.simulatedArrivalAt
                      "
                      class="trip-checkin-simulated"
                      >Giờ mô phỏng:
                      {{ simulatedVisitTime(visitByStop.get(stop.sequenceNumber)!) }}</span
                    ><span
                      v-if="!visitByStop.has(stop.sequenceNumber)"
                      class="trip-checkin-pending"
                      >{{
                        checkins.data?.awaitingExit === true &&
                        checkins.data.nextStopSequence === stop.sequenceNumber
                          ? 'Chờ ra khỏi vùng rồi vào lại'
                          : 'Chưa ghi nhận'
                      }}</span
                    >
                  </div>
                </div>
              </li>
            </ol>
          </section>

          <aside
            class="trip-detail-sidebar"
            aria-label="Tình trạng vận hành"
          >
            <section class="trip-operation-card">
              <div class="trip-operation-card-heading">
                <span><Check :size="17" /></span>
                <div>
                  <small>Tiến độ chuyến</small>
                  <strong>Ghi nhận điểm dừng</strong>
                </div>
                <b>{{ checkInProgress }}%</b>
              </div>
              <div
                class="trip-progress"
                aria-hidden="true"
              >
                <span :style="{ width: `${checkInProgress}%` }"></span>
              </div>
              <div
                class="trip-checkin-summary"
                aria-live="polite"
              >
                <template v-if="checkins.loading">Đang tải ghi nhận check-in…</template
                ><template v-else-if="checkins.error"
                  >
                  <button
                    class="fleet-text-button"
                    @click="checkins.retry"
                  >
                    Tải lại dữ liệu check-in
                  </button></template
                ><template v-else>{{
                  checkins.data?.revision === 0
                    ? 'Chưa có dữ liệu check-in cho chuyến này.'
                    : `Đã ghi nhận ${completedStopCount}/${detail.stops.length} điểm dừng.`
                }}</template>
              </div>
              <p
                v-if="trip.status === 'SCHEDULED' && !canStart && trip.dispatch?.startMode !== 'AUTO_IF_READY'"
                class="fleet-prerequisite"
              >
                Gán tài xế đang hoạt động trước khi khởi hành chuyến này.
              </p>
              <p
                v-if="trip.status === 'IN_PROGRESS' && !canComplete"
                class="fleet-prerequisite"
              >
                Cần ghi nhận trạm cuối trước khi hoàn thành chuyến.
              </p>
            </section>

            <section class="trip-operation-card">
              <div class="trip-operation-card-heading">
                <span><Clock3 :size="17" /></span>
                <div>
                  <small>Dự báo vận hành</small>
                  <strong>Thời gian còn lại</strong>
                </div>
              </div>
              <div
                class="trip-eta-summary"
                aria-live="polite"
              >
                <template v-if="eta.loading && !eta.data">Đang tính ETA theo giao thông…</template
                ><template v-else-if="eta.error"
                  >
                  <button
                    class="fleet-text-button"
                    @click="eta.retry"
                  >
                    Thử lại ETA
                  </button></template
                ><template v-else-if="eta.data"
                  ><strong>{{
                    eta.data.status === 'BLOCKED'
                      ? 'Đường phía trước bị chặn'
                      : `${Math.ceil(eta.data.totalRemainingSeconds / 60)} phút`
                  }}</strong
                  ><small>{{
                    eta.data.status === 'BLOCKED'
                      ? 'Chưa xác định được giờ đến cuối tuyến.'
                      : eta.data.status === 'STALE'
                        ? 'Ước tính theo dữ liệu giao thông gần nhất'
                        : eta.data.source === 'HERE_LIVE'
                          ? 'Còn lại đến cuối tuyến theo giao thông trực tiếp'
                          : trafficSourceLabel[eta.data.source]
                  }}</small></template
                ><template v-else>Chưa có ETA traffic; đang dùng dữ liệu lộ trình đã lưu.</template>
              </div>
            </section>
          </aside>
        </div>
        <RouteRevisionPanel :trip-id="trip.id" />
      </template>
    </div>
    <div
      v-if="trip && !loading && (trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS')"
      class="fleet-footer"
    >
      <button
        class="btn-secondary"
        :disabled="busy"
        @click="
          cancelReason = '';
          confirm = 'cancel';
        "
      >
        <X :size="14" />Hủy chuyến</button
      ><button
        v-if="trip.status === 'SCHEDULED' && trip.dispatch?.startMode !== 'AUTO_IF_READY'"
        class="btn-primary"
        :disabled="busy || !canStart"
        :title="!canStart ? 'Cần gán tài xế active trước khi khởi hành' : undefined"
        @click="onAction('start')"
      >
        <Play :size="14" />{{ busy ? 'Đang xử lý…' : 'Khởi hành' }}</button
      ><button
        v-else
        class="btn-primary"
        :disabled="busy || !canComplete"
        :title="!canComplete ? 'Cần ghi nhận trạm cuối trước khi hoàn thành' : undefined"
        @click="confirm = 'complete'"
      >
        <Check :size="14" />Hoàn thành</button
      ><button
        v-if="trip.status === 'SCHEDULED' && trip.dispatchMode === 'ON_DEMAND' && onDeleteTrip"
        class="danger-action"
        :disabled="busy"
        @click="confirmDelete = true"
      >
        <Trash2 :size="14" />Xóa chuyến
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirm"
      :title="confirm === 'cancel' ? 'Hủy chuyến đi?' : 'Hoàn thành chuyến đi?'"
      :message="
        confirm === 'cancel'
          ? 'Chuyến sẽ kết thúc ở trạng thái đã hủy. Lịch sử được giữ lại.'
          : 'Xác nhận xe đã kết thúc chuyến. Thời điểm hiện tại sẽ được ghi nhận là giờ hoàn thành.'
      "
      :confirm-label="confirm === 'cancel' ? 'Xác nhận hủy chuyến' : 'Xác nhận hoàn thành'"
      :busy="busy"
      :confirm-disabled="confirm === 'cancel' && cancelReason.trim().length < 3"
      :on-close="() => (confirm = null)"
      :on-confirm="confirmAction"
      ><label
        v-if="confirm === 'cancel'"
        class="fleet-confirm-field"
        >Lý do hủy chuyến *<textarea
          v-model="cancelReason"
          maxlength="500"
          rows="3"
          placeholder="Ví dụ: Xe gặp sự cố kỹ thuật…"
        /></label
    ></FleetConfirmDialog>
    <FleetConfirmDialog
      v-if="confirmDelete && trip && onDeleteTrip"
      title="Xóa chuyến chưa khởi hành?"
      message="Chỉ chuyến SCHEDULED chưa có dữ liệu vận hành mới xóa được. Hành động này không thể hoàn tác."
      confirm-label="Xác nhận xóa chuyến"
      :busy="busy"
      :on-close="() => (confirmDelete = false)"
      :on-confirm="deleteTrip"
    />
    <FleetConfirmDialog
      v-if="confirmDriverChange && trip"
      :title="hasPendingAssignment ? 'Thay đổi yêu cầu nhận chuyến?' : 'Đổi tài xế chuyến này?'"
      :message="hasPendingAssignment
        ? selectedDriver
          ? `Yêu cầu hiện tại sẽ bị hủy và gửi yêu cầu mới cho ${selectedDriver.fullName}.`
          : 'Yêu cầu hiện tại sẽ bị hủy; chuyến sẽ chưa có tài xế.'
        : selectedDriver
          ? `Quyền của ${trip.driver?.fullName ?? 'tài xế hiện tại'} sẽ bị thu hồi ngay và gửi yêu cầu nhận chuyến cho ${selectedDriver.fullName}.`
          : 'Tài xế hiện tại sẽ bị bỏ gán khỏi chuyến.'"
      :confirm-label="hasPendingAssignment && !selectedDriver ? 'Hủy yêu cầu' : 'Xác nhận đổi tài xế'"
      :busy="busy"
      :on-close="() => (confirmDriverChange = false)"
      :on-confirm="() => { void commitDriverChange(); }"
    />
  </section>
</template>
