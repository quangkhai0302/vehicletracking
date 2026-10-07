<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import {
  CalendarClock,
  CheckCircle2,
  ChevronRight,
  Pencil,
  Plus,
  Power,
  RotateCcw,
  X,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import {
  createSchedule,
  fetchSchedules,
  setScheduleEnabled,
  updateSchedule,
} from '@/features/schedules/api/schedules';
import { fetchRoutes } from '@/features/routes/api/routes';
import type { Driver, FleetVehicle } from '@/features/fleet/types/fleet';
import type { RouteSummary } from '@/features/routes/types/route';
import type { TripSchedule, TripScheduleInput } from '@/features/schedules/types/schedule';
import PageHeading from '@/shared/components/PageHeading.vue';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import AppSelect from '@/shared/components/AppSelect.vue';
import AppDatePicker from '@/shared/components/AppDatePicker.vue';
import ScheduleConfirm from '@/features/schedules/components/ScheduleConfirm.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { fetchRouteById } from '@/features/routes/api/routes';
import type { RouteStop } from '@/features/routes/types/route';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';
import '@/features/schedules/styles/schedule-management.css';
interface ScheduleData {
  schedules: TripSchedule[];
  routes: RouteSummary[];
  vehicles: FleetVehicle[];
  drivers: Driver[];
}
interface FormState extends TripScheduleInput {
  id: number | null;
}
const weekdays = [
  { bit: 1, label: 'T2' },
  { bit: 2, label: 'T3' },
  { bit: 4, label: 'T4' },
  { bit: 8, label: 'T5' },
  { bit: 16, label: 'T6' },
  { bit: 32, label: 'T7' },
  { bit: 64, label: 'CN' },
];
const blankForm = (): FormState => ({
  id: null,
  name: '',
  routeId: 0,
  vehicleId: 0,
  driverId: 0,
  frequency: 'WEEKLY',
  scheduledDate: null,
  weekdaysMask: 31,
  departureTime: '08:00',
  timezone: 'Asia/Ho_Chi_Minh',
  effectiveFrom: new Date().toLocaleDateString('en-CA'),
  effectiveUntil: null,
  expectedEmployeeBoardings: {},
});
const dateTime = (value: string | null, timezone: string) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: timezone,
      }).format(new Date(value))
    : 'Chưa có';
const dayLabels = (mask: number) =>
  weekdays
    .filter((day) => (mask & day.bit) !== 0)
    .map((day) => day.label)
    .join(', ');
const data = shallowRef<ScheduleData | null>(null),
  loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
const routeFilter = ref(''),
  statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
const schedulesPage = ref(1);
const schedulesPageSize = 10;
const editorOpen = ref(false),
  form = ref<FormState>(blankForm()),
  formError = ref<string | null>(null),
  saving = ref(false);
const routeStops = shallowRef<RouteStop[]>([]), boardingDraft = ref<Record<number, string>>({});
const pendingToggle = shallowRef<TripSchedule | null>(null),
  toggleError = ref<string | null>(null),
  toggling = ref(false);
useErrorToast(error);
useErrorToast(toggleError);
let disposed = false;
onScopeDispose(() => {
  disposed = true;
});
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    Promise.all([
      fetchSchedules(controller.signal),
      fetchRoutes(controller.signal),
      fetchFleetVehicles(controller.signal),
      fetchDrivers(controller.signal),
    ])
      .then(([schedules, routes, vehicles, drivers]) => {
        if (!controller.signal.aborted) data.value = { schedules, routes, vehicles, drivers };
      })
      .catch((reason) => {
        if (!controller.signal.aborted)
          error.value =
            reason instanceof Error ? reason.message : 'Không thể tải lịch chạy tự động.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
watch(() => form.value.routeId, async (routeId) => {
  routeStops.value = []; boardingDraft.value = {};
  if (!routeId) return;
  try {
    const route = await fetchRouteById(routeId);
    routeStops.value = route.stops;
    boardingDraft.value = Object.fromEntries(route.stops
      .filter((stop) => stop.role !== 'END')
      .map((stop) => [stop.sequenceNumber, String(form.value.expectedEmployeeBoardings[stop.sequenceNumber] ?? '')]));
  } catch { routeStops.value = []; }
});
const schedules = computed(() =>
  (data.value?.schedules ?? []).filter(
    (schedule) =>
      (!routeFilter.value || String(schedule.routeId) === routeFilter.value) &&
      (statusFilter.value === 'ALL' ||
        (statusFilter.value === 'ENABLED' ? schedule.enabled : !schedule.enabled)),
  ),
);
const schedulesPageCount = computed(() => Math.max(1, Math.ceil(schedules.value.length / schedulesPageSize)));
const pageSchedules = computed(() => schedules.value.slice((schedulesPage.value - 1) * schedulesPageSize, schedulesPage.value * schedulesPageSize));
watch([routeFilter, statusFilter], () => (schedulesPage.value = 1));
watch(schedulesPageCount, (count) => { if (schedulesPage.value > count) schedulesPage.value = count; });
const metrics = computed(() => ({
  total: data.value?.schedules.length ?? 0,
  active: data.value?.schedules.filter((s) => s.enabled).length ?? 0,
  weekly: data.value?.schedules.filter((s) => s.enabled && s.frequency === 'WEEKLY').length ?? 0,
}));
const activeRoutes = computed(
  () => data.value?.routes.filter((route) => route.active !== false) ?? [],
);
const activeVehicles = computed(
  () => data.value?.vehicles.filter((vehicle) => vehicle.active) ?? [],
);
const activeDrivers = computed(() => data.value?.drivers.filter((driver) => driver.active) ?? []);
const routeFilterOptions = computed(() => [
  { value: '', label: 'Tất cả tuyến' },
  ...(data.value?.routes ?? []).map((r) => ({ value: String(r.id), label: r.name })),
]);

const statusFilterOptions = [
  { value: 'ALL', label: 'Tất cả trạng thái' },
  { value: 'ENABLED', label: 'Đang hoạt động' },
  { value: 'DISABLED', label: 'Đã tạm dừng' },
];

const routeSelectOptions = computed(() => [
  { value: 0, label: 'Chọn tuyến', disabled: true },
  ...activeRoutes.value.map((r) => ({ value: r.id, label: r.name })),
]);

const vehicleSelectOptions = computed(() => [
  { value: 0, label: 'Chọn xe', disabled: true },
  ...activeVehicles.value.map((v) => ({
    value: v.id,
    label: `${v.plateNumber} · ${v.name}`,
  })),
]);

const driverSelectOptions = computed(() => [
  { value: 0, label: 'Chọn tài xế', disabled: true },
  ...activeDrivers.value.map((d) => ({
    value: d.id,
    label: `${d.fullName} · ${d.licenseNumber}`,
  })),
]);
function openCreate() {
  form.value = blankForm();
  boardingDraft.value = {};
  formError.value = null;
  editorOpen.value = true;
}
function openEdit(s: TripSchedule) {
  boardingDraft.value = Object.fromEntries(Object.entries(s.expectedEmployeeBoardings ?? {})
    .map(([sequence, count]) => [Number(sequence), String(count)]));
  form.value = {
    id: s.id,
    name: s.name ?? '',
    routeId: s.routeId,
    vehicleId: s.vehicleId,
    driverId: s.driverId,
    frequency: s.frequency,
    scheduledDate: s.scheduledDate,
    weekdaysMask: s.weekdaysMask,
    departureTime: s.departureTime.slice(0, 5),
    timezone: s.timezone,
    effectiveFrom: s.effectiveFrom,
    effectiveUntil: s.effectiveUntil,
    expectedEmployeeBoardings: { ...(s.expectedEmployeeBoardings ?? {}) },
  };
  formError.value = null;
  editorOpen.value = true;
}
async function save() {
  if (saving.value) return;
  const f = form.value;
  if (
    !f.routeId ||
    !f.vehicleId ||
    !f.driverId ||
    !f.departureTime ||
    !f.timezone ||
    !f.effectiveFrom
  ) {
    formError.value = 'Hãy chọn tuyến, xe, tài xế và điền đầy đủ thời gian hiệu lực.';
    return;
  }
  if (f.frequency === 'ONCE' && !f.scheduledDate) {
    formError.value = 'Lịch một lần cần ngày chạy cụ thể.';
    return;
  }
  if (f.frequency === 'WEEKLY' && f.weekdaysMask === 0) {
    formError.value = 'Hãy chọn ít nhất một ngày trong tuần.';
    return;
  }
  if (f.effectiveUntil && f.effectiveUntil < f.effectiveFrom) {
    formError.value = 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.';
    return;
  }
  saving.value = true;
  formError.value = null;
  const input: TripScheduleInput = {
    name: f.name?.trim() || null,
    routeId: f.routeId,
    vehicleId: f.vehicleId,
    driverId: f.driverId,
    frequency: f.frequency,
    scheduledDate: f.frequency === 'ONCE' ? f.scheduledDate : null,
    weekdaysMask: f.frequency === 'WEEKLY' ? f.weekdaysMask : 0,
    departureTime: f.departureTime,
    timezone: f.timezone.trim(),
    effectiveFrom: f.effectiveFrom,
    effectiveUntil: f.effectiveUntil || null,
    expectedEmployeeBoardings: Object.fromEntries(Object.entries(boardingDraft.value)
      .filter(([, value]) => value !== '')
      .map(([sequence, value]) => [sequence, Number(value)])),
  };
  try {
    const saved = f.id ? await updateSchedule(f.id, input) : await createSchedule(input);
    if (disposed) return;
    if (data.value)
      data.value = {
        ...data.value,
        schedules: f.id
          ? data.value.schedules.map((item) => (item.id === saved.id ? saved : item))
          : [saved, ...data.value.schedules],
      };
    editorOpen.value = false;
    notifySuccess(f.id ? 'Đã cập nhật lịch chạy.' : 'Đã tạo lịch chạy.');
  } catch (reason) {
    if (!disposed)
      notifyError(reason instanceof Error ? reason.message : 'Không thể lưu lịch chạy.');
  } finally {
    if (!disposed) saving.value = false;
  }
}
async function toggle() {
  if (!pendingToggle.value || toggling.value) return;
  toggling.value = true;
  toggleError.value = null;
  try {
    const saved = await setScheduleEnabled(pendingToggle.value.id, !pendingToggle.value.enabled);
    if (disposed) return;
    if (data.value)
      data.value = {
        ...data.value,
        schedules: data.value.schedules.map((item) => (item.id === saved.id ? saved : item)),
      };
    pendingToggle.value = null;
    notifySuccess(saved.enabled ? 'Đã kích hoạt lịch chạy.' : 'Đã tạm dừng lịch chạy.');
  } catch (reason) {
    if (!disposed)
      toggleError.value =
        reason instanceof Error ? reason.message : 'Không thể thay đổi trạng thái lịch.';
  } finally {
    if (!disposed) toggling.value = false;
  }
}
</script>
<template>
  <div class="business-page schedule-page">
    <PageHeading
      eyebrow="ĐIỀU PHỐI HÀNH TRÌNH"
      title="Lịch chạy tự động"
      description="Thiết lập lịch cố định, phân công phương tiện và tài xế cho từng tuyến."
      ><template #actions
        ><button
          class="business-button primary"
          @click="openCreate"
        >
          <Plus :size="17" />Tạo lịch chạy
        </button></template
      ></PageHeading
    >
    <section
      class="schedule-metrics"
      aria-label="Chỉ số lịch chạy"
      :aria-busy="loading"
    >
      <article>
        <CalendarClock :size="20" />
        <div>
          <span>Tổng lịch chạy</span><strong>{{ loading ? '—' : metrics.total }}</strong>
        </div>
      </article>
      <article>
        <CheckCircle2 :size="20" />
        <div>
          <span>Đang hoạt động</span><strong>{{ loading ? '—' : metrics.active }}</strong>
        </div>
      </article>
      <article>
        <RotateCcw :size="20" />
        <div>
          <span>Lặp hàng tuần</span><strong>{{ loading ? '—' : metrics.weekly }}</strong>
        </div>
      </article>
    </section>
    <section class="business-surface schedule-list-panel">
      <div class="schedule-list-heading">
        <div>
          <h2>Danh sách lịch chạy</h2>
          <p>Thay đổi cấu hình chỉ áp dụng cho các chuyến sẽ được tạo sau đó.</p>
        </div>
        <span>{{ loading ? 'Đang tải…' : `${schedules.length} lịch` }}</span>
      </div>
      <div class="schedule-filters">
        <label class="schedule-filter-item">
          <span class="schedule-filter-label">Tuyến</span>
          <AppSelect
            v-model="routeFilter"
            :options="routeFilterOptions"
            placeholder="Tất cả tuyến"
          />
        </label>
        <label class="schedule-filter-item">
          <span class="schedule-filter-label">Trạng thái</span>
          <AppSelect
            v-model="statusFilter"
            :options="statusFilterOptions"
            placeholder="Tất cả trạng thái"
          />
        </label>
        <button
          v-if="routeFilter || statusFilter !== 'ALL'"
          type="button"
          class="schedule-clear-filter"
          @click="
            routeFilter = '';
            statusFilter = 'ALL';
          "
        >
          Xóa bộ lọc
        </button>
      </div>
      <p
        v-if="loading"
        class="schedule-state"
        role="status"
      >
        Đang tải cấu hình lịch chạy…
      </p>
      <div
        v-if="!loading && !error && schedules.length === 0"
        class="schedule-empty"
      >
        <CalendarClock :size="30" /><strong>{{
          data?.schedules.length ? 'Không có lịch phù hợp' : 'Chưa có lịch chạy tự động'
        }}</strong>
        <p>
          {{
            data?.schedules.length
              ? 'Thử thay đổi bộ lọc để xem lịch khác.'
              : 'Tạo lịch đầu tiên để hệ thống tự động lập chuyến theo tuyến.'
          }}
        </p>
        <button
          v-if="!data?.schedules.length"
          class="schedule-button-primary"
          @click="openCreate"
        >
          <Plus :size="15" />Tạo lịch chạy
        </button>
      </div>
      <div
        v-if="!loading && schedules.length > 0"
        class="schedule-cards"
      >
        <article
          v-for="schedule in pageSchedules"
          :key="schedule.id"
          class="schedule-card"
          :data-enabled="schedule.enabled"
        >
          <div class="schedule-card-header">
            <span :class="schedule.enabled ? 'schedule-status active' : 'schedule-status paused'">{{
              schedule.enabled ? 'Đang hoạt động' : 'Tạm dừng'
            }}</span>
            <div>
              <button
                :aria-label="`Sửa lịch ${schedule.name || schedule.routeName}`"
                @click="openEdit(schedule)"
              >
                <Pencil :size="15" /></button
              ><button
                :aria-label="`${schedule.enabled ? 'Tạm dừng' : 'Bật'} lịch ${schedule.name || schedule.routeName}`"
                @click="
                  toggleError = null;
                  pendingToggle = schedule;
                "
              >
                <Power :size="15" />
              </button>
            </div>
          </div>
          <h3>{{ schedule.name || schedule.routeName }}</h3>
          <p class="schedule-route">{{ schedule.routeName }}</p>
          <dl class="schedule-details">
            <div>
              <dt>Lịch chạy</dt>
              <dd>
                {{
                  schedule.frequency === 'ONCE'
                    ? `Một lần · ${schedule.scheduledDate ?? '—'}`
                    : `Hàng tuần · ${dayLabels(schedule.weekdaysMask)}`
                }}
              </dd>
            </div>
            <div>
              <dt>Giờ khởi hành</dt>
              <dd>
                {{ schedule.departureTime.slice(0, 5) }}
                <small>{{ schedule.timezone === 'Asia/Ho_Chi_Minh' ? 'Giờ Việt Nam' : schedule.timezone }}</small>
              </dd>
            </div>
            <div>
              <dt>Xe</dt>
              <dd>{{ schedule.vehiclePlate }}</dd>
            </div>
            <div>
              <dt>Tài xế</dt>
              <dd>{{ schedule.driverName }}</dd>
            </div>
            <div>
              <dt>Lần chạy kế tiếp</dt>
              <dd>{{ dateTime(schedule.nextRunAt, schedule.timezone) }}</dd>
            </div>
          </dl>
          <p
            v-if="schedule.lastRunStatus"
            :class="`schedule-last-run ${schedule.lastRunStatus === 'SUCCESS' ? 'success' : 'failed'}`"
          >
            {{
              schedule.lastRunStatus === 'SUCCESS'
                ? 'Đã tạo chuyến theo lịch gần nhất'
                : 'Lần chạy gần nhất cần chú ý'
            }}{{ schedule.lastRunMessage ? ` · ${schedule.lastRunMessage}` : '' }}
          </p>
          <RouterLink to="/trips">Xem chuyến đi <ChevronRight :size="14" /></RouterLink>
        </article>
      </div>
      <PaginationControls v-if="!loading" v-model:page="schedulesPage" :page-count="schedulesPageCount" :total="schedules.length" :page-size="schedulesPageSize" label="lịch chạy" />
    </section>
    <SidePanel
      v-if="editorOpen"
      class-name="schedule-editor"
      :label="form.id ? 'Chỉnh sửa lịch chạy' : 'Tạo lịch chạy tự động'"
      :busy="saving"
      content-sized
      :on-close="() => (editorOpen = false)"
      ><header>
        <div>
          <span>{{ form.id ? 'CẬP NHẬT LỊCH' : 'LỊCH CHẠY MỚI' }}</span>
          <h2 id="schedule-editor-title">
            {{ form.id ? 'Chỉnh sửa lịch chạy' : 'Tạo lịch chạy tự động' }}
          </h2>
        </div>
        <button
          aria-label="Đóng biểu mẫu"
          :disabled="saving"
          @click="editorOpen = false"
        >
          <X :size="18" />
        </button>
      </header>
      <form
        class="schedule-editor-form"
        @submit.prevent="save"
      >
        <div class="schedule-form-content">
          <p
            v-if="formError"
            class="schedule-inline-error"
            role="alert"
          >
            {{ formError }}
          </p>
          <label>
            <span class="schedule-label-title">Tên lịch</span>
            <input
              v-model="form.name"
              maxlength="150"
              placeholder="Ví dụ: Tuyến sáng ngày thường"
            />
          </label>
          <div class="schedule-form-grid">
            <label>
              <span class="schedule-label-title">Tuyến đường *</span>
              <AppSelect
                :model-value="form.routeId"
                :options="routeSelectOptions"
                placeholder="Chọn tuyến"
                @update:model-value="form.routeId = Number($event)"
              />
            </label>
            <label>
              <span class="schedule-label-title">Phương tiện *</span>
              <AppSelect
                :model-value="form.vehicleId"
                :options="vehicleSelectOptions"
                placeholder="Chọn xe"
                @update:model-value="form.vehicleId = Number($event)"
              />
            </label>
            <label>
              <span class="schedule-label-title">Tài xế *</span>
              <AppSelect
                :model-value="form.driverId"
                :options="driverSelectOptions"
                placeholder="Chọn tài xế"
                @update:model-value="form.driverId = Number($event)"
              />
            </label>
            <label>
              <span class="schedule-label-title">Múi giờ *</span>
              <input
                v-model="form.timezone"
                required
                placeholder="Asia/Ho_Chi_Minh"
              />
            </label>
          </div>
          <fieldset class="schedule-frequency-fieldset">
            <legend>Tần suất</legend>
            <div class="schedule-frequency">
              <label
                class="schedule-frequency-pill"
                :class="{ 'is-selected': form.frequency === 'ONCE' }"
              >
                <input
                  type="radio"
                  name="schedule-freq"
                  :checked="form.frequency === 'ONCE'"
                  @change="form.frequency = 'ONCE'"
                />
                <span>Một lần</span>
              </label>
              <label
                class="schedule-frequency-pill"
                :class="{ 'is-selected': form.frequency === 'WEEKLY' }"
              >
                <input
                  type="radio"
                  name="schedule-freq"
                  :checked="form.frequency === 'WEEKLY'"
                  @change="form.frequency = 'WEEKLY'"
                />
                <span>Hàng tuần</span>
              </label>
            </div>
            <label
              v-if="form.frequency === 'ONCE'"
              class="schedule-date-field"
            >
              <span class="schedule-label-title">Ngày chạy *</span>
              <AppDatePicker
                required
                :model-value="form.scheduledDate"
                placeholder="Chọn ngày chạy"
                @update:model-value="form.scheduledDate = $event || null"
              />
            </label>
            <div
              v-else
              class="schedule-weekdays"
              aria-label="Ngày chạy trong tuần"
            >
              <label
                v-for="day in weekdays"
                :key="day.bit"
                class="schedule-weekday-chip"
              >
                <input
                  type="checkbox"
                  :checked="(form.weekdaysMask & day.bit) !== 0"
                  @change="form.weekdaysMask ^= day.bit"
                />
                <span>{{ day.label }}</span>
              </label>
            </div>
          </fieldset>
          <div class="schedule-form-grid">
            <label>
              <span class="schedule-label-title">Giờ khởi hành *</span>
              <input
                v-model="form.departureTime"
                required
                type="time"
                lang="vi-VN"
              />
            </label>
            <label>
              <span class="schedule-label-title">Hiệu lực từ *</span>
              <AppDatePicker
                required
                :model-value="form.effectiveFrom"
                placeholder="dd/mm/yyyy"
                @update:model-value="form.effectiveFrom = $event"
              />
            </label>
            <label>
              <span class="schedule-label-title">
                Hiệu lực đến
                <span class="schedule-optional-hint">(không bắt buộc)</span>
              </span>
              <AppDatePicker
                :model-value="form.effectiveUntil"
                :min="form.effectiveFrom"
                placeholder="dd/mm/yyyy"
                @update:model-value="form.effectiveUntil = $event || null"
              />
            </label>
          </div>
          <section v-if="routeStops.length" class="schedule-boarding-defaults" aria-label="Số người dự kiến lên xe">
            <div><strong>Số người dự kiến lên xe</strong><p>Điền số dự kiến để tài xế xác nhận khi mô phỏng xe đến từng điểm đón. Để trống nếu muốn nhập tại trạm.</p></div>
            <label v-for="stop in routeStops" :key="stop.sequenceNumber" class="schedule-boarding-row">
              <span>{{ stop.sequenceNumber }}. {{ stop.stationName }}<small v-if="stop.role === 'END'">Điểm đến chung · không đón người</small></span>
              <input v-if="stop.role !== 'END'" v-model="boardingDraft[stop.sequenceNumber]" type="number" min="0" step="1" inputmode="numeric" :aria-label="`Số người lên tại ${stop.stationName}`" placeholder="Nhập số dự kiến" />
              <span v-else class="schedule-boarding-destination">Trường học</span>
            </label>
          </section>
        </div>
        <footer class="schedule-editor-footer">
          <button
            type="button"
            class="schedule-button-secondary"
            :disabled="saving"
            @click="editorOpen = false"
          >
            Hủy
          </button>
          <button
            type="submit"
            class="schedule-button-primary"
            :disabled="saving"
          >
            {{ saving ? 'Đang lưu…' : form.id ? 'Lưu thay đổi' : 'Tạo lịch chạy' }}
          </button>
        </footer>
      </form></SidePanel
    >
    <ScheduleConfirm
      v-if="pendingToggle"
      :schedule="pendingToggle"
      :busy="toggling"
      :on-close="() => (pendingToggle = null)"
      :on-confirm="toggle"
    />
  </div>
</template>
