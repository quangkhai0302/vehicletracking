<script lang="ts">
export type VehicleFilterTab = 'ALL' | 'RUNNING' | 'DELAYED';
</script>
<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  AlertTriangle,
  BusFront,
  Clock3,
  Gauge,
  MapPin,
  MapPinned,
  Radio,
  Route,
  Search,
} from '@lucide/vue';
import type { Vehicle } from '@/features/fleet/types/vehicle';

const props = defineProps<{
  vehicles: Vehicle[];
  selectedVehicleId: string | null;
  onSelectVehicle: (vehicle: Vehicle) => void;
  onManageStations: () => void;
}>();
const query = ref(''),
  filterTab = ref<VehicleFilterTab>('ALL');
const counts = computed(() => ({
  total: props.vehicles.length,
  running: props.vehicles.filter((v) => v.status === 'RUNNING').length,
  delayed: props.vehicles.filter((v) => v.status === 'DELAYED').length,
}));
const filteredVehicles = computed(() => {
  const q = query.value.trim().toLowerCase();
  return props.vehicles.filter(
    (v) =>
      (filterTab.value === 'ALL' || v.status === filterTab.value) &&
      (!q ||
        [v.plateNumber, v.driverName, v.routeName, v.nextStationName].some((value) =>
          value.toLowerCase().includes(q),
        )),
  );
});
function selectOnKey(event: KeyboardEvent, vehicle: Vehicle) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    props.onSelectVehicle(vehicle);
  }
}
</script>

<template>
  <aside
    class="operations-panel"
    aria-label="Theo dõi đội xe"
  >
    <div class="operations-panel-header">
      <div>
        <div class="panel-eyebrow">Tổng quan phương tiện</div>
        <h2>Theo dõi đội xe</h2>
      </div>
      <span class="count-badge tabular-numbers">{{ counts.total }} xe</span>
    </div>
    <div
      class="fleet-metrics"
      aria-label="Tổng quan đội xe"
    >
      <div class="metric-box success">
        <BusFront :size="16" /><strong class="tabular-numbers">{{ counts.running }}</strong
        ><span>Đang chạy</span>
      </div>
      <div class="metric-box warning">
        <Clock3 :size="16" /><strong class="tabular-numbers">{{ counts.delayed }}</strong
        ><span>Trễ lịch</span>
      </div>
      <div class="metric-box info">
        <Radio :size="16" /><strong class="tabular-numbers">{{ counts.total }}</strong
        ><span>Kết nối</span>
      </div>
    </div>
    <div
      v-if="vehicles.length === 0"
      class="operations-empty"
    >
      <div class="operations-empty-icon"><Route :size="28" /></div>
      <span class="empty-state-label">CHƯA KẾT NỐI</span><strong>Đội xe của bạn sẽ ở đây</strong>
      <p>
        Chưa kết nối nguồn vị trí xe. Bạn có thể bắt đầu bằng việc thiết lập trạm và tuyến đường.
      </p>
      <button
        type="button"
        class="primary-action"
        @click="onManageStations"
      >
        <MapPinned :size="16" /> Thiết lập trạm
      </button>
    </div>
    <template v-else>
      <div
        class="vehicle-filter-tabs"
        role="tablist"
        aria-label="Lọc trạng thái xe"
      >
        <button
          type="button"
          role="tab"
          :class="['station-tab-btn', { active: filterTab === 'ALL' }]"
          :aria-selected="filterTab === 'ALL'"
          @click="filterTab = 'ALL'"
        >
          Tất cả ({{ counts.total }})
        </button>
        <button
          type="button"
          role="tab"
          :class="['station-tab-btn', { active: filterTab === 'RUNNING' }]"
          :aria-selected="filterTab === 'RUNNING'"
          @click="filterTab = 'RUNNING'"
        >
          Đang chạy ({{ counts.running }})
        </button>
        <button
          type="button"
          role="tab"
          :class="['station-tab-btn', { active: filterTab === 'DELAYED' }]"
          :aria-selected="filterTab === 'DELAYED'"
          @click="filterTab = 'DELAYED'"
        >
          Trễ lịch ({{ counts.delayed }})
        </button>
      </div>
      <label class="station-search"
        ><Search
          :size="15"
          aria-hidden="true" /><input
          v-model="query"
          placeholder="Tìm theo biển số, tài xế, trạm..."
          aria-label="Tìm kiếm xe"
      /></label>
      <div
        class="vehicle-list"
        aria-live="polite"
      >
        <div
          v-if="filteredVehicles.length === 0"
          class="station-empty compact"
        >
          Không tìm thấy phương tiện phù hợp.
        </div>
        <div
          v-for="vehicle in filteredVehicles"
          :key="vehicle.id"
          :class="[
            'vehicle-card',
            { selected: selectedVehicleId === vehicle.id, delayed: vehicle.status === 'DELAYED' },
          ]"
          role="button"
          :tabindex="0"
          :aria-pressed="selectedVehicleId === vehicle.id"
          @click="onSelectVehicle(vehicle)"
          @keydown="selectOnKey($event, vehicle)"
        >
          <div class="vehicle-card-header">
            <div class="vehicle-plate-badge">
              <span
                :class="['vehicle-live-dot', vehicle.status === 'DELAYED' ? 'delayed' : 'active']"
              /><strong>{{ vehicle.plateNumber }}</strong>
            </div>
            <div class="vehicle-speed-tag tabular-numbers">
              <Gauge :size="12" /><span>{{ vehicle.speedKmh }} km/h</span>
            </div>
          </div>
          <div class="vehicle-card-sub">
            <span class="vehicle-model">{{ vehicle.model }}</span
            ><span class="vehicle-driver">• {{ vehicle.driverName }}</span>
          </div>
          <div class="vehicle-card-next">
            <div class="next-station-row">
              <MapPin
                :size="12"
                class="next-icon"
              /><span
                class="next-name"
                :title="vehicle.nextStationName"
                >{{ vehicle.nextStationName }}</span
              ><span class="next-eta tabular-numbers">ETA ~{{ vehicle.etaMinutes }}p</span>
            </div>
            <div class="vehicle-progress-track">
              <div
                class="vehicle-progress-fill"
                :style="{ width: `${Math.min(100, Math.max(0, vehicle.tripProgressPercent))}%` }"
              />
            </div>
          </div>
          <div
            v-if="vehicle.status === 'DELAYED'"
            class="vehicle-warning-banner"
          >
            <AlertTriangle :size="12" /><span>Chậm lịch trình do mật độ giao thông cao</span>
          </div>
        </div>
      </div>
    </template>
    <div class="operations-panel-footer">
      <Radio :size="15" /><span>Vị trí, vận tốc và ETA cần nguồn dữ liệu xe.</span>
    </div>
  </aside>
</template>
