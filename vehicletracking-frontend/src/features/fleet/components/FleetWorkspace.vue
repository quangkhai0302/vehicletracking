<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import {
  ArrowRight,
  BusFront,
  CalendarDays,
  CarFront,
  CheckCircle2,
  CircleOff,
  Clock3,
  Edit3,
  ListChecks,
  Plus,
  RefreshCw,
  Scooter,
  Search,
  Trash2,
  UserRound,
} from '@lucide/vue';
import {
  TRIP_STATUS_LABELS,
  vehicleTypeLabel,
  type Driver,
  type DriverInput,
  type FleetVehicle,
  type TripDetail,
  type TripStatus,
  type VehicleInput,
} from '@/features/fleet/types/fleet';
import { useFleetWorkspace, type FleetTab } from '@/features/fleet/composables/useFleetWorkspace';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import VehicleEditor from './VehicleEditor.vue';
import TripEditor from './TripEditor.vue';
import TripDetailPanel from './TripDetailPanel.vue';
import FleetConfirmDialog from './FleetConfirmDialog.vue';
import DriverEditor from './DriverEditor.vue';
import FleetManagementTable from './FleetManagementTable.vue';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import SidePanel from '@/shared/components/SidePanel.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import '@/features/fleet/styles/fleet.css';
const props = withDefaults(
  defineProps<{
    onToast: (message: string) => void;
    onFocusStop: (position: [number, number], zoom?: number) => void;
    onManageRoutes: () => void;
    onManageStations: () => void;
    liveSnapshot?: OperationsSnapshot | null;
    onSimulateTrip?: (id: number) => void;
    onViewRoute?: (id: number) => void;
    onFocusVehicle?: (id: number) => void;
    tripSelection?: { tripId: number | null } | null;
    onTripCreated?: (detail: TripDetail) => void;
    initialTab?: FleetTab;
    initialVehicleFilter?: number | null;
    initialRouteId?: number | null;
    openTripFromRoute?: boolean;
    onExitRoutePrefill?: () => void;
    lockedTab?: FleetTab;
    onViewVehicleTrips?: (vehicleId: number) => void;
    onClearVehicleFilter?: () => void;
  }>(),
  { initialTab: 'vehicles', initialVehicleFilter: null },
);
const fleet = useFleetWorkspace(
  (message) => props.onToast(message),
  () => props.liveSnapshot ?? null,
  () => props.tripSelection ?? null,
  (detail) => props.onTripCreated?.(detail),
  props.initialTab,
  props.initialVehicleFilter,
);
useErrorToast(() => fleet.error);
watch(
  [() => props.initialRouteId, () => props.openTripFromRoute],
  ([routeId, open]) => {
    if (props.lockedTab === 'trips' && open && routeId) fleet.openTripForm();
  },
  { immediate: true },
);
const activeTab = computed(() => props.lockedTab ?? fleet.tab),
  screen = computed(() => fleet.screen);
watch(screen, (current, previous) => {
  if (previous.kind === 'trip-form' && current.kind !== 'trip-form' && props.openTripFromRoute)
    props.onExitRoutePrefill?.();
});
const query = ref(''),
  vehicleStatus = ref<'active' | 'inactive' | 'all'>('active'),
  driverStatus = ref<'active' | 'inactive' | 'all'>('active'),
  tripStatus = ref<TripStatus | ''>('');
const deactivate = shallowRef<FleetVehicle | null>(null),
  deactivateDriver = shallowRef<Driver | null>(null);
const q = computed(() => query.value.trim().toLocaleLowerCase('vi'));
const vehicles = computed(() =>
  fleet.vehicles.filter(
    (vehicle) =>
      (vehicleStatus.value === 'all' || vehicle.active === (vehicleStatus.value === 'active')) &&
      (!q.value ||
        `${vehicle.plateNumber} ${vehicle.name} ${vehicle.driver?.fullName ?? ''}`
          .toLocaleLowerCase('vi')
          .includes(q.value)),
  ),
);
const drivers = computed(() =>
  fleet.drivers.filter(
    (driver) =>
      (driverStatus.value === 'all' || driver.active === (driverStatus.value === 'active')) &&
      (!q.value ||
        `${driver.fullName} ${driver.phoneNumber} ${driver.licenseNumber}`
          .toLocaleLowerCase('vi')
          .includes(q.value)),
  ),
);
const trips = computed(() =>
  fleet.trips.filter(
    (trip) =>
      (!fleet.vehicleFilter || trip.vehicleId === fleet.vehicleFilter) &&
      (!tripStatus.value || trip.status === tripStatus.value) &&
      (!q.value ||
        `${trip.id} ${trip.vehiclePlateNumber} ${trip.routeName}`
          .toLocaleLowerCase('vi')
          .includes(q.value)),
  ),
);
const filteredVehicle = computed(() =>
  fleet.vehicles.find((vehicle) => vehicle.id === fleet.vehicleFilter),
);
const hasFilters = computed(() =>
  Boolean(
    query.value.trim() ||
    (activeTab.value === 'vehicles' && vehicleStatus.value !== 'active') ||
    (activeTab.value === 'drivers' && driverStatus.value !== 'active') ||
    (activeTab.value === 'trips' && (tripStatus.value || fleet.vehicleFilter)),
  ),
);
function clearVehicleFilter() {
  fleet.setVehicleFilter(null);
  if (activeTab.value === 'trips') props.onClearVehicleFilter?.();
}
function clearFilters() {
  query.value = '';
  vehicleStatus.value = 'active';
  driverStatus.value = 'active';
  tripStatus.value = '';
  clearVehicleFilter();
}
const moduleCopy = computed(() =>
  activeTab.value === 'vehicles'
    ? {
        eyebrow: 'ĐỘI XE',
        title: 'Phương tiện',
        description: 'Quản lý danh mục xe, trạng thái sử dụng và phân công hiện tại.',
        icon: BusFront,
        primary: 'Thêm phương tiện',
        total: fleet.vehicles.length,
        primaryAction: () => fleet.openVehicleForm(null),
      }
    : activeTab.value === 'drivers'
      ? {
          eyebrow: 'NHÂN SỰ VẬN HÀNH',
          title: 'Tài xế',
          description: 'Theo dõi hồ sơ, giấy phép và xe đang được phân công.',
          icon: UserRound,
          primary: 'Thêm tài xế',
          total: fleet.drivers.length,
          primaryAction: () => fleet.openDriverForm(null),
        }
      : {
          eyebrow: 'ĐIỀU PHỐI VẬN HÀNH',
          title: 'Chuyến đi',
          description: 'Điều phối chuyến tức thời, phân công và theo dõi vòng đời vận hành.',
          icon: CalendarDays,
          primary: 'Điều phối chuyến ngay',
          total: fleet.trips.length,
          primaryAction: fleet.openTripForm,
        },
);
const summaryCards = computed(() => {
  if (activeTab.value === 'vehicles') {
    return [
      {
        label: 'Tổng phương tiện',
        value: fleet.vehicles.length,
        hint: 'Trong danh mục đội xe',
        tone: 'total',
        icon: BusFront,
      },
      {
        label: 'Đang sử dụng',
        value: fleet.vehicles.filter((vehicle) => vehicle.active).length,
        hint: 'Sẵn sàng điều phối',
        tone: 'active',
        icon: CheckCircle2,
      },
      {
        label: 'Đã ngừng sử dụng',
        value: fleet.vehicles.filter((vehicle) => !vehicle.active).length,
        hint: 'Không tham gia vận hành',
        tone: 'inactive',
        icon: CircleOff,
      },
      {
        label: 'Đã phân công tài xế',
        value: fleet.vehicles.filter((vehicle) => vehicle.active && vehicle.driver).length,
        hint: 'Trên số xe đang sử dụng',
        tone: 'assigned',
        icon: UserRound,
      },
    ];
  }
  if (activeTab.value === 'drivers') {
    return [
      {
        label: 'Tổng tài xế',
        value: fleet.drivers.length,
        hint: 'Hồ sơ trong hệ thống',
        tone: 'total',
        icon: UserRound,
      },
      {
        label: 'Đang hoạt động',
        value: fleet.drivers.filter((driver) => driver.active).length,
        hint: 'Có thể nhận phân công',
        tone: 'active',
        icon: CheckCircle2,
      },
      {
        label: 'Đã ngừng hoạt động',
        value: fleet.drivers.filter((driver) => !driver.active).length,
        hint: 'Không thể nhận chuyến',
        tone: 'inactive',
        icon: CircleOff,
      },
      {
        label: 'Đang được gán xe',
        value: fleet.drivers.filter((driver) =>
          fleet.vehicles.some((vehicle) => vehicle.active && vehicle.driver?.id === driver.id),
        ).length,
        hint: 'Phân công phương tiện hiện tại',
        tone: 'assigned',
        icon: CarFront,
      },
    ];
  }
  return [
    {
      label: 'Tổng chuyến đi',
      value: fleet.trips.length,
      hint: 'Toàn bộ chuyến đã tạo',
      tone: 'total',
      icon: ListChecks,
    },
    {
      label: 'Đang thực hiện',
      value: fleet.trips.filter((trip) => trip.status === 'IN_PROGRESS').length,
      hint: 'Đang vận hành trên tuyến',
      tone: 'active',
      icon: CarFront,
    },
    {
      label: 'Chờ khởi hành',
      value: fleet.trips.filter((trip) => trip.status === 'SCHEDULED').length,
      hint: 'Sẵn sàng bắt đầu',
      tone: 'waiting',
      icon: Clock3,
    },
    {
      label: 'Đã hoàn thành',
      value: fleet.trips.filter((trip) => trip.status === 'COMPLETED').length,
      hint: 'Kết thúc hành trình',
      tone: 'completed',
      icon: CheckCircle2,
    },
  ];
});
const filteredCount = computed(() =>
  activeTab.value === 'vehicles'
    ? vehicles.value.length
    : activeTab.value === 'drivers'
      ? drivers.value.length
      : trips.value.length,
);
const hasActiveVehicle = computed(() => fleet.vehicles.some((vehicle) => vehicle.active));
const primaryDisabled = computed(
  () => fleet.loading || !!fleet.error || (activeTab.value === 'trips' && !hasActiveVehicle.value),
);
function viewVehicleTrips(id: number) {
  query.value = '';
  if (props.onViewVehicleTrips) props.onViewVehicleTrips(id);
  else fleet.showVehicleTrips(id);
}
const assignedVehicle = (id: number) =>
  fleet.vehicles.find((vehicle) => vehicle.active && vehicle.driver?.id === id);
const assignedVehicleLabel = (id: number) => {
  const v = assignedVehicle(id);
  return v ? `${v.plateNumber} · ${v.name}` : 'Chưa gán';
};
async function saveVehicle(input: VehicleInput, id?: number) {
  const saved = await fleet.saveVehicle(input, id);
  if (saved) {
    query.value = '';
    vehicleStatus.value = 'active';
  }
  return saved;
}
async function saveDriver(input: DriverInput, id?: number) {
  const saved = await fleet.saveDriver(input, id);
  if (saved) {
    query.value = '';
    driverStatus.value = 'active';
  }
  return saved;
}
function retryTrip() {
  if (screen.value.kind === 'trip-detail') void fleet.selectTrip(screen.value.id);
}
async function removeVehicle() {
  if (deactivate.value && (await fleet.removeVehicle(deactivate.value))) deactivate.value = null;
}
async function removeDriver() {
  if (deactivateDriver.value && (await fleet.removeDriver(deactivateDriver.value)))
    deactivateDriver.value = null;
}
</script>
<template>
  <div class="fleet-workspace">
    <div
      class="fleet-list-view"
      :hidden="screen.kind === 'trip-detail'"
    >
      <header class="fleet-module-header">
        <div class="fleet-module-title">
          <span class="fleet-module-icon"
            ><component
              :is="moduleCopy.icon"
              :size="21"
          /></span>
          <div>
            <span class="fleet-module-eyebrow">{{ moduleCopy.eyebrow }}</span>
            <h2>{{ moduleCopy.title }}</h2>
            <p>{{ moduleCopy.description }}</p>
          </div>
        </div>
        <div class="fleet-module-summary">
          <article
            v-for="card in summaryCards"
            :key="card.label"
            :class="['fleet-summary-card', card.tone]"
          >
            <span class="fleet-summary-icon">
              <component
                :is="card.icon"
                :size="20"
              />
            </span>
            <div>
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <small>{{ card.hint }}</small>
            </div>
          </article>
        </div>
        <div class="fleet-module-actions">
          <button
            class="fleet-refresh-button"
            :disabled="fleet.loading || fleet.busy"
            :aria-label="`Tải lại ${moduleCopy.title.toLocaleLowerCase('vi')}`"
            @click="fleet.reload"
          >
            <RefreshCw :size="16" /></button
          ><button
            class="fleet-primary-action"
            :disabled="primaryDisabled"
            :title="
              activeTab === 'trips' && !hasActiveVehicle
                ? 'Cần có ít nhất một xe đang sử dụng'
                : undefined
            "
            @click="moduleCopy.primaryAction"
          >
            <Plus :size="16" />{{ moduleCopy.primary }}
          </button>
        </div>
      </header>
      <div
        v-if="!lockedTab"
        class="planning-tabs fleet-tabs"
        aria-label="Quản lý đội xe"
      >
        <button
          :aria-pressed="activeTab === 'vehicles'"
          @click="
            fleet.setTab('vehicles');
            query = '';
          "
        >
          <BusFront :size="14" />Đội xe
          <span>{{ fleet.vehicles.filter((vehicle) => vehicle.active).length }}</span></button
        ><button
          :aria-pressed="activeTab === 'drivers'"
          @click="
            fleet.setTab('drivers');
            query = '';
          "
        >
          <UserRound :size="14" />Tài xế
          <span>{{ fleet.drivers.filter((driver) => driver.active).length }}</span></button
        ><button
          :aria-pressed="activeTab === 'trips'"
          @click="
            fleet.setTab('trips');
            query = '';
          "
        >
          <CalendarDays :size="14" />Chuyến đi <span>{{ fleet.trips.length }}</span>
        </button>
      </div>
      <div class="fleet-heading">
        <div>
          <span class="panel-eyebrow">DANH SÁCH HIỂN THỊ</span>
          <h2>{{ filteredCount }} kết quả{{ hasFilters ? ' phù hợp' : '' }}</h2>
        </div>
        <span class="fleet-list-hint"><CheckCircle2 :size="14" /> Dữ liệu hiện tại</span>
      </div>
      <div class="fleet-list-tools">
        <label class="fleet-search"
          ><Search :size="15" /><input
            v-model="query"
            :aria-label="
              activeTab === 'vehicles'
                ? 'Tìm xe'
                : activeTab === 'drivers'
                  ? 'Tìm tài xế'
                  : 'Tìm chuyến đi'
            "
            :placeholder="
              activeTab === 'vehicles'
                ? 'Biển số, tên xe hoặc tài xế…'
                : activeTab === 'drivers'
                  ? 'Tên, điện thoại hoặc GPLX…'
                  : 'Biển số, tuyến hoặc mã chuyến…'
            " /></label
        ><select
          v-if="activeTab === 'vehicles'"
          v-model="vehicleStatus"
          aria-label="Lọc xe"
        >
          <option value="active">Đang sử dụng</option>
          <option value="inactive">Đã ngừng sử dụng</option>
          <option value="all">Tất cả xe</option></select
        ><select
          v-else-if="activeTab === 'drivers'"
          v-model="driverStatus"
          aria-label="Lọc tài xế"
        >
          <option value="active">Đang hoạt động</option>
          <option value="inactive">Đã ngừng hoạt động</option>
          <option value="all">Tất cả tài xế</option></select
        ><template v-else
          ><select
            v-model="tripStatus"
            aria-label="Lọc trạng thái chuyến"
          >
            <option value="">Mọi trạng thái</option>
            <option
              v-for="(label, id) in TRIP_STATUS_LABELS"
              :key="id"
              :value="id"
            >
              {{ label }}
            </option>
          </select>
          <div
            v-if="fleet.vehicleFilter"
            class="fleet-filter-chip"
          >
            <span>Xe {{ filteredVehicle?.plateNumber ?? fleet.vehicleFilter }}</span
            ><button @click="clearVehicleFilter">Bỏ lọc xe</button>
          </div></template
        ><button
          v-if="hasFilters"
          class="fleet-clear-filters"
          @click="clearFilters"
        >
          Xóa bộ lọc
        </button>
      </div>
      <div
        class="fleet-list-body"
        :aria-busy="fleet.loading"
      >
        <p
          v-if="fleet.loading"
          class="fleet-loading"
          role="status"
        >
          Đang tải xe và chuyến đi…
        </p>
        <FleetManagementTable
          v-if="!fleet.loading && !fleet.error && lockedTab"
          :tab="activeTab"
          :vehicles="vehicles"
          :all-vehicles="fleet.vehicles"
          :drivers="drivers"
          :trips="trips"
          :on-edit-vehicle="fleet.openVehicleForm"
          :on-deactivate-vehicle="(vehicle) => (deactivate = vehicle)"
          :on-vehicle-trips="viewVehicleTrips"
          :on-edit-driver="fleet.openDriverForm"
          :on-deactivate-driver="(driver) => (deactivateDriver = driver)"
          :on-trip="fleet.selectTrip"
        />
        <template v-if="!lockedTab && !fleet.loading && !fleet.error && activeTab === 'vehicles'">
          <div
            v-if="vehicles.length === 0"
            class="fleet-empty"
          >
            <BusFront :size="30" />
            <h3>
              {{
                fleet.vehicles.length ? 'Không tìm thấy xe phù hợp' : 'Chưa có xe trong danh mục'
              }}
            </h3>
            <p>Thêm xe, sau đó tạo chuyến từ tuyến đã lưu.</p>
            <button
              class="fleet-text-button"
              @click="onManageStations"
            >
              Thiết lập trạm
            </button>
          </div>
          <article
            v-for="vehicle in vehicles"
            :key="vehicle.id"
            class="fleet-vehicle-card"
          >
            <div class="fleet-card-main">
              <button
                class="fleet-vehicle-select"
                @click="viewVehicleTrips(vehicle.id)"
              >
                <span class="fleet-plate"
                  ><Scooter
                    v-if="vehicle.vehicleType === 'MOTORCYCLE'"
                    :size="16"
                  /><CarFront
                    v-else
                    :size="16"
                  />{{ vehicle.plateNumber }}</span
                ><strong>{{ vehicle.name }}</strong
                ><span class="fleet-help"
                  >{{ vehicleTypeLabel(vehicle.vehicleType) }} ·
                  {{ vehicle.active ? 'Đang sử dụng' : 'Đã ngừng sử dụng' }} ·
                  {{
                    liveSnapshot == null
                      ? 'Realtime xem tại Giám sát trực tiếp'
                      : liveSnapshot.positions.some((point) => point.vehicleId === vehicle.id)
                        ? 'Đã nhận vị trí'
                        : 'Chưa có vị trí xe'
                  }}</span
                ><span class="fleet-help"
                  >Tài xế: {{ vehicle.driver?.fullName ?? 'Chưa gán' }}</span
                >
              </button>
              <div
                v-if="vehicle.active"
                class="fleet-card-actions"
              >
                <button
                  class="fleet-icon-button"
                  :aria-label="`Sửa xe ${vehicle.plateNumber}`"
                  @click="fleet.openVehicleForm(vehicle)"
                >
                  <Edit3 :size="15" /></button
                ><button
                  class="fleet-icon-button danger"
                  :aria-label="`Ngừng sử dụng xe ${vehicle.plateNumber}`"
                  @click="deactivate = vehicle"
                >
                  <Trash2 :size="15" />
                </button>
              </div>
            </div>
            <p
              v-if="vehicle.description"
              class="fleet-card-description"
            >
              {{ vehicle.description }}
            </p>
            <button
              v-if="liveSnapshot?.positions.some((point) => point.vehicleId === vehicle.id)"
              class="fleet-text-button"
              @click="onFocusVehicle?.(vehicle.id)"
            >
              Xem vị trí xe</button
            ><button
              class="fleet-text-button"
              @click="viewVehicleTrips(vehicle.id)"
            >
              Xem chuyến đi <span>→</span>
            </button>
          </article>
        </template>
        <template v-if="!lockedTab && !fleet.loading && !fleet.error && activeTab === 'drivers'"
          ><div
            v-if="drivers.length === 0"
            class="fleet-empty"
          >
            <UserRound :size="30" />
            <h3>{{ fleet.drivers.length ? 'Không tìm thấy tài xế phù hợp' : 'Chưa có tài xế' }}</h3>
            <p>Thêm tài xế để phân công cho xe và chuyến đi.</p>
          </div>
          <article
            v-for="driver in drivers"
            :key="driver.id"
            class="fleet-driver-card"
          >
            <div class="fleet-card-main">
              <div class="fleet-driver-main">
                <span class="fleet-driver-license"
                  ><UserRound :size="15" />{{ driver.licenseNumber }}</span
                ><strong>{{ driver.fullName }}</strong
                ><span class="fleet-help"
                  >{{ driver.phoneNumber }} ·
                  {{ driver.active ? 'Đang hoạt động' : 'Đã ngừng hoạt động' }}</span
                ><span class="fleet-help">Xe hiện tại: {{ assignedVehicleLabel(driver.id) }}</span>
              </div>
              <div
                v-if="driver.active"
                class="fleet-card-actions"
              >
                <button
                  class="fleet-icon-button"
                  :aria-label="`Sửa tài xế ${driver.fullName}`"
                  @click="fleet.openDriverForm(driver)"
                >
                  <Edit3 :size="15" /></button
                ><button
                  class="fleet-icon-button danger"
                  :aria-label="`Ngừng tài xế ${driver.fullName}`"
                  @click="deactivateDriver = driver"
                >
                  <Trash2 :size="15" />
                </button>
              </div>
            </div></article
        ></template>
        <template v-if="!lockedTab && !fleet.loading && !fleet.error && activeTab === 'trips'"
          ><div
            v-if="trips.length === 0"
            class="fleet-empty"
          >
            <CalendarDays :size="30" />
            <h3>Chưa có chuyến phù hợp</h3>
            <p>Chọn xe và tuyến để tạo chuyến điều phối tức thời.</p>
          </div>
          <button
            v-for="trip in trips"
            :key="trip.id"
            class="fleet-trip-card"
            :aria-label="`Mở chi tiết chuyến ${trip.id}, ${trip.routeName}`"
            @click="fleet.selectTrip(trip.id)"
          >
            <span class="fleet-trip-title"
              ><strong>#{{ trip.id }} · {{ trip.vehiclePlateNumber }}</strong
              ><span :class="`trip-status ${trip.status.toLowerCase()}`">{{
                TRIP_STATUS_LABELS[trip.status]
              }}</span></span
            ><span class="fleet-trip-route">{{ trip.routeName }}</span
            ><span class="fleet-help">
              {{
                trip.dispatchMode === 'FIXED_SCHEDULE'
                  ? `Theo lịch cố định · ${displayTripTime(trip.scheduledDepartureAt)}`
                  : `Điều phối tức thời · ${displayTripTime(trip.createdAt)}`
              }}
            </span>
            <span class="fleet-help">Tài xế: {{ trip.driver?.fullName ?? 'Chưa gán' }}</span>
            <span
              v-if="trip.dispatchMode === 'FIXED_SCHEDULE'"
              class="fleet-help"
              >Hoàn thành theo lịch: {{ displayTripTime(trip.plannedEndAt) }}</span
            ><ArrowRight
              class="fleet-card-arrow"
              :size="16"
              aria-hidden="true"
            /></button
        ></template>
      </div>
      <div class="fleet-footer fleet-list-footer">
        <button
          v-if="activeTab === 'vehicles'"
          class="btn-primary"
          :disabled="fleet.loading || !!fleet.error"
          @click="fleet.openVehicleForm(null)"
        >
          <Plus :size="16" />Thêm xe mới</button
        ><button
          v-else-if="activeTab === 'drivers'"
          class="btn-primary"
          :disabled="fleet.loading || !!fleet.error"
          @click="fleet.openDriverForm(null)"
        >
          <Plus :size="16" />Thêm tài xế mới</button
        ><button
          v-else
          class="btn-primary"
          :disabled="fleet.loading || !!fleet.error || !hasActiveVehicle"
          @click="fleet.openTripForm"
        >
          <Plus :size="16" />Điều phối chuyến ngay
        </button>
      </div>
      <p
        v-if="activeTab === 'trips' && !fleet.loading && !hasActiveVehicle"
        class="fleet-prerequisite"
      >
        Thêm ít nhất một xe đang sử dụng ở mục Phương tiện.
      </p>
    </div>
    <SidePanel
      v-if="screen.kind === 'vehicle-form'"
      class-name="fleet-workspace-modal fleet-form-modal"
      :label="screen.vehicle ? 'Chỉnh sửa phương tiện' : 'Thêm phương tiện'"
      :busy="fleet.busy"
      :on-close="() => undefined"
    >
      <VehicleEditor
        :key="screen.vehicle?.id ?? 'new'"
        :vehicle="screen.vehicle"
        :drivers="fleet.drivers"
        :busy="fleet.busy"
        :on-save="saveVehicle"
        :on-close="fleet.close"
      />
    </SidePanel>
    <SidePanel
      v-if="screen.kind === 'driver-form'"
      class-name="fleet-workspace-modal fleet-form-modal"
      :label="screen.driver ? 'Chỉnh sửa tài xế' : 'Thêm tài xế'"
      :busy="fleet.busy"
      :on-close="() => undefined"
    >
      <DriverEditor
        :key="screen.driver?.id ?? 'new'"
        :driver="screen.driver"
        :busy="fleet.busy"
        :on-save="saveDriver"
        :on-close="fleet.close"
      />
    </SidePanel>
    <SidePanel
      v-if="screen.kind === 'trip-form'"
      class-name="fleet-workspace-modal fleet-form-modal"
      label="Điều phối chuyến đi"
      :busy="fleet.busy"
      :on-close="() => undefined"
    >
      <TripEditor
        :key="`${screen.vehicleId ?? 'none'}:${initialRouteId ?? 'none'}`"
        :vehicles="fleet.vehicles"
        :drivers="fleet.drivers"
        :initial-vehicle-id="screen.vehicleId"
        :initial-route-id="initialRouteId"
        :busy="fleet.busy"
        :on-save="fleet.saveTrip"
        :on-close="fleet.close"
        :on-manage-routes="onManageRoutes"
      />
    </SidePanel>
    <TripDetailPanel
      v-if="screen.kind === 'trip-detail'"
      :key="`${screen.id}:${fleet.detail?.trip.attemptNumber ?? 'loading'}`"
      :detail="fleet.detail"
      :loading="fleet.loadingDetail"
      :busy="fleet.busy"
      :on-close="fleet.close"
      :drivers="fleet.drivers"
      :on-retry="retryTrip"
      :on-action="fleet.transition"
      :on-update-driver="fleet.updateTripDriver"
      :on-delete-trip="fleet.removeTrip"
      :on-focus-stop="onFocusStop"
      :on-simulate="onSimulateTrip"
      :on-view-route="onViewRoute"
      :live-snapshot="liveSnapshot"
    />
    <FleetConfirmDialog
      v-if="deactivate"
      :title="`Ngừng sử dụng xe ${deactivate.plateNumber}?`"
      message="Các chuyến chưa kết thúc phải được hoàn thành hoặc hủy trước. Lịch sử xe và chuyến đi vẫn được lưu."
      confirm-label="Xác nhận ngừng sử dụng xe"
      :busy="fleet.busy"
      :on-close="() => (deactivate = null)"
      :on-confirm="removeVehicle"
    />
    <FleetConfirmDialog
      v-if="deactivateDriver"
      :title="`Ngừng tài xế ${deactivateDriver.fullName}?`"
      message="Tài xế phải được bỏ gán khỏi xe và các chuyến chưa kết thúc. Lịch sử chuyến đã hoàn thành vẫn được giữ."
      confirm-label="Xác nhận ngừng tài xế"
      :busy="fleet.busy"
      :on-close="() => (deactivateDriver = null)"
      :on-confirm="removeDriver"
    />
  </div>
</template>
