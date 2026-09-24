<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import { ArrowLeft, Play, RefreshCw } from '@lucide/vue';
import type { Driver, FleetVehicle, TripInput } from '@/features/fleet/types/fleet';
import { vehicleTypeLabel } from '@/features/fleet/types/fleet';
import type { RouteSummary } from '@/features/routes/types/route';
import { fetchRoutes } from '@/features/routes/api/routes';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import { formatDuration } from '@/shared/utils/format';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
const props = defineProps<{
  vehicles: FleetVehicle[];
  drivers: Driver[];
  initialVehicleId: number | null;
  initialRouteId?: number | null;
  busy: boolean;
  error: string | null;
  onSave: (input: TripInput) => Promise<boolean>;
  onClose: () => void;
  onManageRoutes: () => void;
}>();
const initialDriverId = props.vehicles.find((vehicle) => vehicle.id === props.initialVehicleId)
  ?.driver?.id;
const vehicleId = ref(props.initialVehicleId ? String(props.initialVehicleId) : ''),
  routeId = ref(props.initialRouteId ? String(props.initialRouteId) : ''),
  driverId = ref(initialDriverId ? String(initialDriverId) : '');
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
const valid = computed(
  () =>
    props.vehicles.some((vehicle) => vehicle.id === Number(vehicleId.value) && vehicle.active) &&
    !!selectedRoute.value &&
    !loading.value &&
    !routeError.value,
);
const dirty = computed(
  () =>
    routeId.value !== (props.initialRouteId ? String(props.initialRouteId) : '') ||
    vehicleId.value !== (props.initialVehicleId ? String(props.initialVehicleId) : '') ||
    driverId.value !== (initialDriverId ? String(initialDriverId) : ''),
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
      driverId: driverId.value ? Number(driverId.value) : null,
    });
}
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
        <span class="panel-eyebrow">ĐIỀU PHỐI TỨC THỜI</span>
        <h2>Điều phối chuyến ngay</h2>
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
          v-if="initialRouteId && !loading && !routeError && !selectedRoute"
          class="fleet-error"
          role="alert"
        >
          Tuyến #{{ initialRouteId }} không còn khả dụng. Hãy chọn tuyến khác đang hoạt động.
        </p>
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
        <p class="fleet-help trip-dispatch-help">
          Chuyến được tạo ở trạng thái chờ khởi hành. Thời gian thực tế bắt đầu khi điều phối viên
          hoặc tài xế bấm khởi hành; chuyến chạy cố định được cấu hình tại Lịch chạy tự động.
        </p>
        <div
          v-if="selectedRoute"
          class="trip-preview"
        >
          <span>Thời lượng tuyến ước tính</span
          ><strong>{{ formatDuration(selectedRoute.estimatedTripDurationSeconds) }}</strong
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
        <Play :size="15" />{{ busy ? 'Đang tạo…' : 'Tạo chuyến tức thời' }}
      </button>
    </div>
    <FleetConfirmDialog
      v-if="confirm"
      title="Bỏ bản nháp chuyến đi?"
      message="Xe, tài xế và tuyến đang chọn chưa được lưu."
      confirm-label="Bỏ bản nháp"
      :busy="false"
      :on-confirm="onClose"
      :on-close="() => (confirm = false)"
    />
  </section>
</template>
