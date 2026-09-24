<script setup lang="ts">
import { Play } from '@lucide/vue';
import { TRIP_STATUS_LABELS, type TripSummary } from '@/features/fleet/types/fleet';
import { displayTripTime, tripDispatchLabel } from '@/features/fleet/utils/tripTime';
import type { TelemetryPosition } from '@/features/tracking/types/operations';

defineProps<{
  trip: TripSummary | null;
  position: TelemetryPosition | null;
  simulationBusy: boolean;
  simulationDisabledReason: string | null;
  onStartSimulation: (tripId: number) => void;
  onClear: () => void;
}>();
</script>

<template>
  <div class="tracking-vehicle-card">
    <template v-if="trip">
      <span class="tracking-vehicle-status">{{ TRIP_STATUS_LABELS[trip.status] }}</span>
      <h2>{{ trip.vehiclePlateNumber }}</h2>
      <p class="tracking-vehicle-route">{{ trip.routeName }}</p>
      <dl>
        <div>
          <dt>Chuyến đi</dt>
          <dd>#{{ trip.id }}</dd>
        </div>
        <div>
          <dt>Tài xế</dt>
          <dd>{{ trip.driver?.fullName ?? 'Chưa phân công' }}</dd>
        </div>
        <div>
          <dt>Hình thức</dt>
          <dd>{{ tripDispatchLabel(trip) }}</dd>
        </div>
        <div v-if="trip.dispatchMode === 'FIXED_SCHEDULE'">
          <dt>Khởi hành kế hoạch</dt>
          <dd>{{ displayTripTime(trip.scheduledDepartureAt) }}</dd>
        </div>
      </dl>
      <p
        v-if="position"
        class="tracking-vehicle-hint"
      >
        Vị trí từ {{ position.source === 'SIMULATOR' ? 'mô phỏng' : 'GPS' }} · cập nhật
        {{ displayTripTime(position.recordedAt) }}.
      </p>
      <template v-if="trip.status === 'SCHEDULED'">
        <button
          type="button"
          class="tracking-vehicle-start"
          :disabled="simulationBusy || !!simulationDisabledReason"
          @click="onStartSimulation(trip.id)"
        >
          <Play
            :size="16"
            aria-hidden="true"
          />
          {{ simulationBusy ? 'Đang bắt đầu…' : 'Bắt đầu mô phỏng' }}
        </button>
        <p
          v-if="simulationDisabledReason"
          class="tracking-vehicle-start-note"
          role="status"
        >
          {{ simulationDisabledReason }}
        </p>
      </template>
      <RouterLink
        to="/trips"
        class="tracking-vehicle-link"
        >Mở quản lý chuyến đi →</RouterLink
      >
    </template>
    <p
      v-else
      role="status"
    >
      Chưa tải được thông tin chuyến đã chọn.
    </p>
    <button
      type="button"
      class="tracking-vehicle-clear"
      @click="onClear"
    >
      Bỏ chọn xe
    </button>
  </div>
</template>
