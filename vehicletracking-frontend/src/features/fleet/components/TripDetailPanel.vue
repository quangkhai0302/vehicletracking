<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  ArrowLeft,
  CalendarClock,
  Check,
  MapPin,
  Play,
  RefreshCw,
  Trash2,
  UserRound,
  X,
} from '@lucide/vue';
import {
  TRIP_STATUS_LABELS,
  type Driver,
  type TripAction,
  type TripDetail,
} from '@/features/fleet/types/fleet';
import { displayTripTime, toLocalDateTimeInput } from '@/features/fleet/utils/tripTime';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
import { useTripCheckIns } from '@/features/fleet/composables/useTripCheckIns';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import type { StopVisit } from '@/features/fleet/types/checkin';
import { useTripEta } from '@/features/fleet/composables/useTripEta';
import { trafficSourceLabel } from '@/features/traffic/utils/tripTraffic';
import RouteRevisionPanel from './RouteRevisionPanel.vue';
const props = defineProps<{
  detail: TripDetail | null;
  loading: boolean;
  busy: boolean;
  error: string | null;
  drivers: Driver[];
  onClose: () => void;
  onRetry: () => void;
  onAction: (action: TripAction, reason?: string) => Promise<boolean>;
  onUpdateSchedule?: (id: number, input: { scheduledDepartureAt: string }) => Promise<boolean>;
  onUpdateDriver?: (id: number, driverId: number | null) => Promise<boolean>;
  onDeleteTrip?: (id: number) => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onSimulate?: (id: number) => void;
  liveSnapshot?: OperationsSnapshot | null;
}>();
const confirm = ref<'complete' | 'cancel' | null>(null),
  cancelReason = ref(''),
  editingSchedule = ref(false),
  editingDriver = ref(false),
  driverId = ref(''),
  schedule = ref(''),
  confirmDelete = ref(false);
const trip = computed(() => props.detail?.trip);
const checkins = useTripCheckIns(
  () => trip.value?.id ?? null,
  () => props.liveSnapshot ?? null,
);
const eta = useTripEta(() => trip.value?.id ?? null);
const visitByStop = computed(
  () => new Map(checkins.data?.visits.map((visit) => [visit.stopSequence, visit]) ?? []),
);
const etaByStop = computed(
  () => new Map(eta.data?.stops.map((stop) => [stop.sequenceNumber, stop]) ?? []),
);
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
    !!trip.value.driver &&
    props.drivers.some((driver) => driver.id === trip.value?.driver?.id && driver.active),
);
const etaLabel = computed(() =>
  eta.data?.source === 'HERE_LIVE' && eta.data.status !== 'STALE'
    ? 'Dự kiến đến (theo giao thông)'
    : eta.data?.source === 'HERE_LAST_KNOWN' || eta.data?.status === 'STALE'
      ? 'Dự kiến đến (dữ liệu gần nhất)'
      : 'Dự kiến đến (theo lịch)',
);
function beginScheduleEdit() {
  if (trip.value) {
    schedule.value = toLocalDateTimeInput(new Date(trip.value.scheduledDepartureAt));
    editingSchedule.value = true;
  }
}
async function saveSchedule() {
  if (!trip.value || !props.onUpdateSchedule) return;
  const date = new Date(schedule.value);
  if (!Number.isFinite(date.getTime())) return;
  if (await props.onUpdateSchedule(trip.value.id, { scheduledDepartureAt: date.toISOString() }))
    editingSchedule.value = false;
}
function beginDriverEdit() {
  if (trip.value) {
    driverId.value = trip.value.driver ? String(trip.value.driver.id) : '';
    editingDriver.value = true;
  }
}
async function saveDriver() {
  if (
    trip.value &&
    props.onUpdateDriver &&
    (await props.onUpdateDriver(trip.value.id, driverId.value ? Number(driverId.value) : null))
  )
    editingDriver.value = false;
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
function visitTime(visit: StopVisit) {
  return displayTripTime(
    visit.source === 'SIMULATOR' && visit.simulatedArrivalAt
      ? visit.simulatedArrivalAt
      : visit.actualArrivalAt,
  );
}
</script>
<template>
  <section
    class="fleet-editor"
    aria-label="Chi tiết chuyến đi"
  >
    <div class="fleet-heading">
      <button
        class="fleet-icon-button"
        :disabled="busy"
        aria-label="Đóng chi tiết chuyến"
        @click="onClose"
      >
        <ArrowLeft :size="18" />
      </button>
      <div>
        <span class="panel-eyebrow">LỊCH TRÌNH CHUYẾN ĐI</span>
        <h2>{{ trip ? `Chuyến #${trip.id}` : 'Chi tiết chuyến' }}</h2>
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
    <div class="fleet-detail-body">
      <p
        v-if="loading"
        role="status"
        class="fleet-loading"
      >
        Đang tải lịch trình…
      </p>
      <div
        v-if="error && !confirm"
        class="fleet-error"
        role="alert"
      >
        {{ error
        }}<button
          class="fleet-text-button"
          :disabled="busy || loading"
          @click="onRetry"
        >
          Tải lại trạng thái chuyến
        </button>
      </div>
      <template v-if="!loading && trip && detail">
        <div class="trip-summary">
          <span :class="`trip-status ${trip.status.toLowerCase()}`">{{
            TRIP_STATUS_LABELS[trip.status]
          }}</span>
          <h3>{{ trip.vehiclePlateNumber }}</h3>
          <p>{{ trip.routeName }}</p>
          <p>
            <UserRound :size="12" />
            {{
              trip.driver
                ? `${trip.driver.fullName} · ${trip.driver.licenseNumber} · ${trip.driver.phoneNumber}`
                : 'Chưa gán tài xế'
            }}
          </p>
        </div>
        <details class="trip-traffic-details">
          <summary>Lịch trình dự kiến và thực tế</summary>
          <dl class="trip-times">
            <div>
              <dt>Xuất phát kế hoạch</dt>
              <dd>{{ displayTripTime(trip.scheduledDepartureAt) }}</dd>
            </div>
            <div>
              <dt>Hoàn thành theo lịch</dt>
              <dd>{{ displayTripTime(trip.plannedEndAt) }}</dd>
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
        </details>
        <p
          v-if="trip.status === 'CANCELLED' && trip.cancellationReason"
          class="fleet-help trip-cancellation-reason"
        >
          <strong>Lý do hủy:</strong> {{ trip.cancellationReason }}
        </p>
        <div
          v-if="editingSchedule"
          class="trip-schedule-editor"
        >
          <label
            >Giờ xuất phát mới<input
              v-model="schedule"
              type="datetime-local"
              min="2000-01-01T00:00"
              max="2100-12-31T23:59"
          /></label>
          <div>
            <button
              class="btn-secondary"
              :disabled="busy"
              @click="editingSchedule = false"
            >
              Hủy</button
            ><button
              class="btn-primary"
              :disabled="busy || !schedule"
              @click="saveSchedule"
            >
              <CalendarClock :size="14" />Lưu giờ xuất phát
            </button>
          </div>
        </div>
        <button
          v-else-if="trip.status === 'SCHEDULED' && onUpdateSchedule"
          class="fleet-text-button"
          :disabled="busy"
          @click="beginScheduleEdit"
        >
          <CalendarClock :size="14" />Sửa giờ xuất phát
        </button>
        <div
          v-if="editingDriver"
          class="trip-schedule-editor"
        >
          <label
            >Tài xế thực hiện<select v-model="driverId">
              <option value="">Chưa gán tài xế</option>
              <option
                v-for="driver in drivers.filter((item) => item.active)"
                :key="driver.id"
                :value="String(driver.id)"
              >
                {{ driver.fullName }} · {{ driver.licenseNumber }}
              </option>
            </select></label
          >
          <div>
            <button
              class="btn-secondary"
              :disabled="busy"
              @click="editingDriver = false"
            >
              Hủy</button
            ><button
              class="btn-primary"
              :disabled="busy"
              @click="saveDriver"
            >
              <UserRound :size="14" />Lưu tài xế
            </button>
          </div>
        </div>
        <button
          v-else-if="trip.status === 'SCHEDULED' && onUpdateDriver"
          class="fleet-text-button"
          :disabled="busy"
          @click="beginDriverEdit"
        >
          <UserRound :size="14" />Đổi tài xế
        </button>
        <div
          class="trip-checkin-summary"
          aria-live="polite"
        >
          <template v-if="checkins.loading">Đang tải ghi nhận check-in…</template
          ><template v-else-if="checkins.error"
            >{{ checkins.error }}
            <button
              class="fleet-text-button"
              @click="checkins.retry"
            >
              Tải lại
            </button></template
          ><template v-else>{{
            checkins.data?.revision === 0
              ? 'Chưa có dữ liệu check-in cho chuyến này.'
              : `Đã ghi nhận ${checkins.data?.visits.length ?? 0}/${detail.stops.length} điểm dừng.`
          }}</template>
        </div>
        <p
          v-if="trip.status === 'SCHEDULED' && !canStart"
          class="fleet-prerequisite"
        >
          Gán tài xế active trước khi khởi hành chuyến này.
        </p>
        <p
          v-if="trip.status === 'IN_PROGRESS' && !canComplete"
          class="fleet-prerequisite"
        >
          Cần ghi nhận trạm cuối trước khi hoàn thành chuyến.
        </p>
        <div
          class="trip-eta-summary"
          aria-live="polite"
        >
          <template v-if="eta.loading && !eta.data">Đang tính ETA theo giao thông…</template
          ><template v-else-if="eta.error"
            >{{ eta.error }}
            <button
              class="fleet-text-button"
              @click="eta.retry"
            >
              Thử lại ETA
            </button></template
          ><template v-else-if="eta.data"
            ><strong>{{
              eta.data.status === 'BLOCKED'
                ? 'Đường phía trước bị chặn, chưa xác định giờ đến.'
                : `Còn khoảng ${Math.ceil(eta.data.totalRemainingSeconds / 60)} phút đến cuối tuyến`
            }}</strong
            ><small v-if="eta.data.source !== 'HERE_LIVE' || eta.data.status === 'STALE'">{{
              eta.data.status === 'STALE'
                ? 'Ước tính theo dữ liệu giao thông gần nhất'
                : trafficSourceLabel[eta.data.source]
            }}</small></template
          ><template v-else>Chưa có ETA traffic; đang dùng lịch tuyến đã lưu.</template>
        </div>
        <button
          v-if="onSimulate"
          class="fleet-text-button"
          :disabled="busy"
          @click="onSimulate(trip.id)"
        >
          <Play :size="14" />Mở điều khiển chuyến này
        </button>
        <ol class="trip-timeline">
          <li
            v-for="(stop, index) in detail.stops"
            :key="stop.sequenceNumber"
          >
            <span
              :class="`stop-order ${index === 0 ? 'start' : index === detail.stops.length - 1 ? 'end' : 'stop'}`"
              >{{ stop.sequenceNumber }}</span
            >
            <div>
              <button
                class="fleet-stop-link"
                @click="onFocusStop([stop.latitude, stop.longitude], 16)"
              >
                <span>{{ stop.stationName }}</span
                ><MapPin :size="13" /></button
              ><span class="fleet-help">{{
                index === 0
                  ? 'Điểm đầu'
                  : index === detail.stops.length - 1
                    ? 'Điểm cuối'
                    : 'Trạm dừng'
              }}</span
              ><span
                v-if="!visitByStop.has(stop.sequenceNumber)"
                class="trip-eta-stop"
                >{{ etaLabel }}:
                {{
                  etaByStop.get(stop.sequenceNumber)?.etaAt
                    ? displayTripTime(etaByStop.get(stop.sequenceNumber)!.etaAt)
                    : eta.data?.status === 'BLOCKED'
                      ? 'Đường bị đóng'
                      : displayTripTime(stop.plannedArrivalAt)
                }}</span
              ><span
                v-if="visitByStop.has(stop.sequenceNumber)"
                class="trip-checkin-done"
                >Đã qua trạm lúc {{ visitTime(visitByStop.get(stop.sequenceNumber)!) }}</span
              ><span
                v-else
                class="trip-checkin-pending"
                >{{
                  checkins.data?.awaitingExit === true &&
                  checkins.data.nextStopSequence === stop.sequenceNumber
                    ? 'Chờ ra khỏi vùng rồi vào lại'
                    : 'Chưa ghi nhận'
                }}</span
              >
            </div>
          </li>
        </ol>
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
        v-if="trip.status === 'SCHEDULED'"
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
        v-if="trip.status === 'SCHEDULED' && onDeleteTrip"
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
      :error="error"
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
      :error="error"
      :on-close="() => (confirmDelete = false)"
      :on-confirm="deleteTrip"
    />
  </section>
</template>
