<script setup lang="ts">
import { computed } from 'vue';
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
    <div class="simulation-fleet-heading">
      <strong>{{ fleet.trips.length }} xe · {{ running }} đang chạy</strong
      ><button
        type="button"
        :disabled="!fleet.trips.length"
        @click="onFit"
      >
        Xem tất cả
      </button>
    </div>
    <p class="simulation-route-legend">Tuyến xanh cyan: xe đang chọn · Các màu khác: xe còn lại.</p>
    <p
      v-if="fleet.loading"
      role="status"
    >
      Đang tải lộ trình đội xe…
    </p>
    <div
      v-if="fleet.routeFailures.length > 0"
      role="alert"
    >
      <p>
        Chưa hiển thị tuyến:
        {{ fleet.routeFailures.map((item) => item.trip.vehiclePlateNumber).join(', ') }}.
      </p>
      <button
        type="button"
        @click="fleet.retry"
      >
        Thử tải lại tuyến
      </button>
    </div>
    <details
      :key="selectedTripId ?? 'all'"
      :open="selectedTripId === null || undefined"
      class="simulation-fleet-picker"
    >
      <summary>{{ selectedTripId === null ? 'Danh sách xe mô phỏng' : 'Chọn xe khác' }}</summary>
      <p>Bấm xe trên bản đồ hoặc chọn bên dưới để điều khiển riêng. Các xe khác tiếp tục chạy.</p>
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
          <span
            ><strong
              ><i
                class="simulation-route-swatch"
                :style="{
                  background: simulationRouteColor(trip.vehicleId, trip.id === selectedTripId),
                }"
                aria-hidden="true"
              />{{ trip.vehiclePlateNumber }}</strong
            ><small>{{ run ? SIMULATION_LABELS[run.status] : 'Chờ xuất phát' }}</small></span
          >
          <span
            >{{ vehicleTypeLabel(trip.vehicleType) }} ·
            {{ preview ? `Trạm đầu: ${preview.start.stationName}` : trip.routeName }}</span
          >
        </button>
      </div>
      <p
        v-if="!snapshot"
        role="status"
      >
        Đang tải đội xe…
      </p>
      <p v-if="snapshot && !fleet.trips.length">
        Chưa có xe chờ hoặc đang mô phỏng. Tạo xe và gán chuyến trước khi chạy.
      </p>
      <p
        v-if="fleet.loading"
        role="status"
      >
        Đang tải vị trí trạm đầu…
      </p>
      <div
        v-if="fleet.failures.length > 0"
        role="alert"
      >
        <p>{{ fleet.failures.length }} xe chưa tải được trạm đầu.</p>
        <button
          type="button"
          @click="fleet.retry"
        >
          Thử lại vị trí chờ
        </button>
      </div>
      <button
        type="button"
        @click="onManage"
      >
        Quản lý xe, tuyến &amp; lịch khởi hành
      </button>
    </details>
  </section>
</template>
