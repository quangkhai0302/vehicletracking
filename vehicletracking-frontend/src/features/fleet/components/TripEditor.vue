<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import { ArrowLeft, CalendarPlus, RefreshCw } from '@lucide/vue';
import type { Driver, FleetVehicle, TripInput } from '@/features/fleet/types/fleet';
import { vehicleTypeLabel } from '@/features/fleet/types/fleet';
import type { RouteSummary } from '@/features/routes/types/route';
import { fetchRoutes } from '@/features/routes/api/routes';
import { displayTripTime, toLocalDateTimeInput } from '@/features/fleet/utils/tripTime';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
const props = defineProps<{
  vehicles: FleetVehicle[];
  drivers: Driver[];
  initialVehicleId: number | null;
  busy: boolean;
  error: string | null;
  onSave: (input: TripInput) => Promise<boolean>;
  onClose: () => void;
  onManageRoutes: () => void;
}>();
const initialDriverId = props.vehicles.find((vehicle) => vehicle.id === props.initialVehicleId)
  ?.driver?.id;
const vehicleId = ref(props.initialVehicleId ? String(props.initialVehicleId) : ''),
  routeId = ref(''),
  driverId = ref(initialDriverId ? String(initialDriverId) : '');
const initialDeparture = toLocalDateTimeInput(new Date(Date.now() + 15 * 60000)),
  departure = ref(initialDeparture);
const routes = shallowRef<RouteSummary[]>([]),
  loading = ref(true),
  routeError = ref<string | null>(null),
  attempt = ref(0),
  confirm = ref(false);
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    fetchRoutes(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          routes.value = data;
          routeError.value = null;
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted)
          routeError.value = err instanceof Error ? err.message : 'Không thể tải tuyến.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
function retry() {
  loading.value = true;
  routeError.value = null;
  attempt.value++;
}
const selectedRoute = computed(() =>
  routes.value.find((route) => route.id === Number(routeId.value)),
);
const date = computed(() => new Date(departure.value));
const validTime = computed(
  () =>
    Number.isFinite(date.value.getTime()) &&
    date.value.getTime() >= Date.UTC(2000, 0, 1) &&
    date.value.getTime() < Date.UTC(2101, 0, 1),
);
const valid = computed(
  () =>
    props.vehicles.some((vehicle) => vehicle.id === Number(vehicleId.value) && vehicle.active) &&
    !!selectedRoute.value &&
    validTime.value &&
    !loading.value &&
    !routeError.value,
);
const dirty = computed(
  () =>
    routeId.value !== '' ||
    vehicleId.value !== (props.initialVehicleId ? String(props.initialVehicleId) : '') ||
    driverId.value !== (initialDriverId ? String(initialDriverId) : '') ||
    departure.value !== initialDeparture,
);
function close() {
  if (dirty.value) confirm.value = true;
  else props.onClose();
}
function selectVehicle(event: Event) {
  vehicleId.value = (event.target as HTMLSelectElement).value;
  const vehicle = props.vehicles.find((item) => item.id === Number(vehicleId.value));
  driverId.value = vehicle?.driver ? String(vehicle.driver.id) : '';
}
function submit() {
  if (!props.busy && valid.value)
    void props.onSave({
      vehicleId: Number(vehicleId.value),
      routeId: Number(routeId.value),
      scheduledDepartureAt: date.value.toISOString(),
      driverId: driverId.value ? Number(driverId.value) : null,
    });
}
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
</script>
<template>
  <section
    class="fleet-editor"
    aria-label="Tạo chuyến đi"
  >
    <div class="fleet-heading">
      <button
        class="fleet-icon-button"
        aria-label="Đóng biểu mẫu chuyến"
        :disabled="busy"
        @click="close"
      >
        <ArrowLeft :size="18" />
      </button>
      <div>
        <span class="panel-eyebrow">LẬP LỊCH VẬN HÀNH</span>
        <h2>Tạo chuyến đi</h2>
      </div>
    </div>
    <form
      id="trip-form"
      class="fleet-form-body"
      @submit.prevent="submit"
    >
      <fieldset :disabled="busy">
        <p
          v-if="error"
          class="fleet-error"
          role="alert"
        >
          {{ error }}
        </p>
        <label
          >Xe thực hiện *<select
            aria-label="Xe thực hiện *"
            :value="vehicleId"
            required
            @change="selectVehicle"
          >
            <option value="">Chọn xe</option>
            <option
              v-for="vehicle in vehicles.filter((item) => item.active)"
              :key="vehicle.id"
              :value="String(vehicle.id)"
            >
              {{ vehicle.plateNumber }} · {{ vehicleTypeLabel(vehicle.vehicleType) }} ·
              {{ vehicle.name }}
            </option>
          </select></label
        >
        <label
          >Tài xế thực hiện <span class="fleet-help">(bắt buộc trước khi khởi hành)</span
          ><select
            v-model="driverId"
            aria-label="Tài xế thực hiện"
          >
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
        <label
          >Tuyến đường *<select
            v-model="routeId"
            aria-label="Tuyến đường *"
            required
            :disabled="loading || !!routeError"
          >
            <option value="">{{ loading ? 'Đang tải tuyến…' : 'Chọn tuyến đã lưu' }}</option>
            <option
              v-for="route in routes"
              :key="route.id"
              :value="String(route.id)"
            >
              {{ route.name }}
            </option>
          </select></label
        >
        <p
          v-if="routeError"
          class="fleet-error"
          role="alert"
        >
          {{ routeError }}
        </p>
        <div class="fleet-inline-actions">
          <button
            type="button"
            class="fleet-text-button"
            :disabled="loading"
            @click="retry"
          >
            <RefreshCw :size="13" />Tải lại tuyến</button
          ><button
            type="button"
            class="fleet-text-button"
            @click="onManageRoutes"
          >
            Quản lý tuyến
          </button>
        </div>
        <p
          v-if="!loading && !routeError && routes.length === 0"
          class="fleet-help"
        >
          Chưa có tuyến. Tạo tuyến ở mục Tuyến & trạm, sau đó quay lại và tải lại danh sách.
        </p>
        <label
          >Giờ xuất phát *<input
            v-model="departure"
            type="datetime-local"
            required
            min="2000-01-01T00:00"
            max="2100-12-31T23:59"
        /></label>
        <p class="fleet-help">
          Giờ địa phương: {{ timezone }}. Lịch kế hoạch được giữ nguyên khi xe khởi hành trễ.
        </p>
        <div
          v-if="validTime && selectedRoute"
          class="trip-preview"
        >
          <span>Dự kiến hoàn thành</span
          ><strong>{{
            displayTripTime(
              new Date(
                date.getTime() + selectedRoute.estimatedTripDurationSeconds * 1000,
              ).toISOString(),
            )
          }}</strong
          ><span
            >{{ selectedRoute.stopCount }} điểm ·
            {{ (selectedRoute.totalDistanceMeters / 1000).toFixed(1) }} km</span
          >
        </div>
        <p class="route-snapshot-note">
          Thời gian dựa trên tuyến đã tính lúc
          {{ selectedRoute ? displayTripTime(selectedRoute.calculatedAt) : 'tạo tuyến' }}. Chưa phải
          ETA theo giao thông hiện tại.
        </p>
      </fieldset>
    </form>
    <div class="fleet-footer">
      <button
        class="btn-secondary"
        :disabled="busy"
        @click="close"
      >
        Hủy</button
      ><button
        class="btn-primary"
        type="submit"
        form="trip-form"
        :disabled="busy || !valid"
      >
        <CalendarPlus :size="15" />{{ busy ? 'Đang tạo…' : 'Tạo chuyến' }}
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirm"
      title="Bỏ bản nháp chuyến đi?"
      message="Xe, tuyến và giờ xuất phát chưa lưu sẽ bị xóa."
      confirm-label="Bỏ bản nháp"
      :busy="false"
      :on-confirm="onClose"
      :on-close="() => (confirm = false)"
    />
  </section>
</template>
