<script setup lang="ts">
import { computed } from 'vue';
import { Maximize2 } from '@lucide/vue';
import type { useSimulationFleet } from '@/features/simulation/composables/useSimulationFleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { SIMULATION_LABELS } from '@/features/tracking/types/operations';
import { vehicleTypeLabel } from '@/features/fleet/types/fleet';
import { simulationRouteColor } from '@/features/simulation/utils/simulationFleet';

const props = defineProps<{
  fleet: ReturnType<typeof useSimulationFleet>;
  snapshot: OperationsSnapshot | null;
  selectedTripId: number | null;
  disabled: boolean;
  onSelect: (tripId: number) => void;
  onFit: () => void;
  onManage: () => void;
}>();

const running = computed(
  () =>
    props.fleet.trips.filter((trip) =>
      props.snapshot?.simulations.some((run) => run.tripId === trip.id && run.status === 'RUNNING'),
    ).length,
);
const rows = computed(() =>
  props.fleet.trips.map((trip) => ({
    trip,
    run: props.snapshot?.simulations.find((item) => item.tripId === trip.id),
    preview: props.fleet.previews.find((item) => item.trip.id === trip.id),
  })),
);
</script>

<template>
  <section
    class="simulation-fleet-list"
    aria-label="Đội xe mô phỏng"
  >
    <div class="simulation-fleet-overview">
      <div>
        <strong>{{ fleet.trips.length }}</strong>
        <span>Xe mô phỏng</span>
      </div>
      <div>
        <strong>{{ running }}</strong>
        <span>Đang chạy</span>
      </div>
      <button
        type="button"
        :disabled="!fleet.trips.length"
        aria-label="Đưa toàn bộ xe mô phỏng vào khung nhìn"
        @click="onFit"
      >
        <Maximize2 :size="14" />
        Toàn đội
      </button>
    </div>

    <details
      :key="selectedTripId ?? 'all'"
      :open="selectedTripId === null"
      class="simulation-fleet-picker"
    >
      <summary>
        <span>{{ selectedTripId === null ? 'Chọn xe mô phỏng' : 'Đổi xe điều khiển' }}</span>
        <small>{{ rows.length }} xe khả dụng</small>
      </summary>
      <div class="simulation-fleet-rows">
        <button
          v-for="{ trip, run, preview } in rows"
          :key="trip.id"
          type="button"
          :data-simulation-trip="trip.id"
          :disabled="disabled"
          :aria-pressed="trip.id === selectedTripId"
          @click="onSelect(trip.id)"
        >
          <span>
            <strong>
              <i
                class="simulation-route-swatch"
                :style="{
                  background: simulationRouteColor(trip.vehicleId, trip.id === selectedTripId),
                }"
                aria-hidden="true"
              />
              {{ trip.vehiclePlateNumber }}
            </strong>
            <small>{{ run ? SIMULATION_LABELS[run.status] : 'Chờ xuất phát' }}</small>
          </span>
          <span>
            {{ vehicleTypeLabel(trip.vehicleType) }} ·
            {{ preview ? `Trạm đầu: ${preview.start.stationName}` : trip.routeName }}
          </span>
        </button>
      </div>
      <p
        v-if="!snapshot"
        role="status"
      >
        Đang tải đội xe…
      </p>
      <p v-if="snapshot && !fleet.trips.length">Chưa có xe chờ hoặc đang mô phỏng.</p>
      <p
        v-if="fleet.loading"
        role="status"
      >
        Đang tải vị trí trạm đầu…
      </p>
      <button
        type="button"
        class="simulation-manage-link"
        @click="onManage"
      >
        Quản lý xe, tuyến và chuyến đi
      </button>
    </details>
  </section>
</template>
