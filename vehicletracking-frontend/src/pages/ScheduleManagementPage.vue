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
  TriangleAlert,
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
import SidePanel from '@/shared/components/SidePanel.vue';
import ScheduleConfirm from '@/features/schedules/components/ScheduleConfirm.vue';
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
const editorOpen = ref(false),
  form = ref<FormState>(blankForm()),
  formError = ref<string | null>(null),
  saving = ref(false);
const pendingToggle = shallowRef<TripSchedule | null>(null),
  toggleError = ref<string | null>(null),
  toggling = ref(false);
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
const schedules = computed(() =>
  (data.value?.schedules ?? []).filter(
    (schedule) =>
      (!routeFilter.value || String(schedule.routeId) === routeFilter.value) &&
      (statusFilter.value === 'ALL' ||
        (statusFilter.value === 'ENABLED' ? schedule.enabled : !schedule.enabled)),
  ),
);
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
function openCreate() {
  form.value = blankForm();
  formError.value = null;
  editorOpen.value = true;
}
function openEdit(s: TripSchedule) {
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
  };
  formError.value = null;
  editorOpen.value = true;
}
function updateId(key: 'routeId' | 'vehicleId' | 'driverId', event: Event) {
  form.value[key] = Number((event.target as HTMLSelectElement).value);
}
function updateNullableDate(key: 'scheduledDate' | 'effectiveUntil', event: Event) {
  form.value[key] = (event.target as HTMLInputElement).value || null;
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
  } catch (reason) {
    if (!disposed)
      formError.value = reason instanceof Error ? reason.message : 'Không thể lưu lịch chạy.';
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
    <section
      v-if="error"
      class="schedule-error"
      role="alert"
    >
      <TriangleAlert :size="19" />
      <div>
        <strong>Không thể tải lịch chạy</strong>
        <p>{{ error }}</p>
      </div>
      <button
        @click="
          loading = true;
          error = null;
          attempt++;
        "
      >
        Thử lại
      </button>
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
        <label
          >Tuyến<select v-model="routeFilter">
            <option value="">Tất cả tuyến</option>
            <option
              v-for="route in data?.routes"
              :key="route.id"
              :value="String(route.id)"
            >
              {{ route.name }}
            </option>
          </select></label
        ><label
          >Trạng thái<select v-model="statusFilter">
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ENABLED">Đang hoạt động</option>
            <option value="DISABLED">Đã tạm dừng</option>
          </select></label
        ><button
          v-if="routeFilter || statusFilter !== 'ALL'"
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
          v-for="schedule in schedules"
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
          <dl>
            <div>
              <dt>Tần suất</dt>
              <dd>
                {{
                  schedule.frequency === 'ONCE'
                    ? `Một lần · ${schedule.scheduledDate ?? '—'}`
                    : `Hàng tuần · ${dayLabels(schedule.weekdaysMask)}`
                }}
              </dd>
            </div>
            <div>
              <dt>Khởi hành</dt>
              <dd>{{ schedule.departureTime.slice(0, 5) }} · {{ schedule.timezone }}</dd>
            </div>
            <div>
              <dt>Phân công</dt>
              <dd>{{ schedule.vehiclePlate }} · {{ schedule.driverName }}</dd>
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
    </section>
    <SidePanel
      v-if="editorOpen"
      class-name="schedule-editor"
      :label="form.id ? 'Chỉnh sửa lịch chạy' : 'Tạo lịch chạy tự động'"
      :busy="saving"
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
      <form @submit.prevent="save">
        <p
          v-if="formError"
          class="schedule-inline-error"
          role="alert"
        >
          {{ formError }}
        </p>
        <label
          >Tên lịch
          <input
            v-model="form.name"
            maxlength="150"
            placeholder="Ví dụ: Tuyến sáng ngày thường"
        /></label>
        <div class="schedule-form-grid">
          <label
            >Tuyến đường<select
              required
              :value="form.routeId || ''"
              @change="updateId('routeId', $event)"
            >
              <option value="">Chọn tuyến</option>
              <option
                v-for="route in activeRoutes"
                :key="route.id"
                :value="route.id"
              >
                {{ route.name }}
              </option>
            </select></label
          ><label
            >Phương tiện<select
              required
              :value="form.vehicleId || ''"
              @change="updateId('vehicleId', $event)"
            >
              <option value="">Chọn xe</option>
              <option
                v-for="vehicle in activeVehicles"
                :key="vehicle.id"
                :value="vehicle.id"
              >
                {{ vehicle.plateNumber }} · {{ vehicle.name }}
              </option>
            </select></label
          ><label
            >Tài xế<select
              required
              :value="form.driverId || ''"
              @change="updateId('driverId', $event)"
            >
              <option value="">Chọn tài xế</option>
              <option
                v-for="driver in activeDrivers"
                :key="driver.id"
                :value="driver.id"
              >
                {{ driver.fullName }} · {{ driver.licenseNumber }}
              </option>
            </select></label
          ><label
            >Múi giờ<input
              v-model="form.timezone"
              required
              placeholder="Asia/Ho_Chi_Minh"
          /></label>
        </div>
        <fieldset>
          <legend>Tần suất</legend>
          <div class="schedule-frequency">
            <label
              ><input
                type="radio"
                :checked="form.frequency === 'ONCE'"
                @change="form.frequency = 'ONCE'"
              />Một lần</label
            ><label
              ><input
                type="radio"
                :checked="form.frequency === 'WEEKLY'"
                @change="form.frequency = 'WEEKLY'"
              />Hàng tuần</label
            >
          </div>
          <label v-if="form.frequency === 'ONCE'"
            >Ngày chạy<input
              required
              type="date"
              :value="form.scheduledDate ?? ''"
              @input="updateNullableDate('scheduledDate', $event)"
          /></label>
          <div
            v-else
            class="schedule-weekdays"
            aria-label="Ngày chạy trong tuần"
          >
            <label
              v-for="day in weekdays"
              :key="day.bit"
              ><input
                type="checkbox"
                :checked="(form.weekdaysMask & day.bit) !== 0"
                @change="form.weekdaysMask ^= day.bit"
              /><span>{{ day.label }}</span></label
            >
          </div>
        </fieldset>
        <div class="schedule-form-grid">
          <label
            >Giờ khởi hành<input
              v-model="form.departureTime"
              required
              type="time" /></label
          ><label
            >Hiệu lực từ<input
              v-model="form.effectiveFrom"
              required
              type="date" /></label
          ><label
            >Hiệu lực đến <small>(không bắt buộc)</small
            ><input
              type="date"
              :value="form.effectiveUntil ?? ''"
              :min="form.effectiveFrom"
              @input="updateNullableDate('effectiveUntil', $event)"
          /></label>
        </div>
        <footer>
          <button
            type="button"
            class="schedule-button-secondary"
            :disabled="saving"
            @click="editorOpen = false"
          >
            Hủy</button
          ><button
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
      :error="toggleError"
      :on-close="() => (pendingToggle = null)"
      :on-confirm="toggle"
    />
  </div>
</template>
