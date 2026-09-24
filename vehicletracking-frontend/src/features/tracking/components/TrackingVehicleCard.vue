<script setup lang="ts">
import { TRIP_STATUS_LABELS, type TripSummary } from '@/features/fleet/types/fleet';
import { displayTripTime } from '@/features/fleet/utils/tripTime';
import type { TelemetryPosition } from '@/features/tracking/types/operations';

defineProps<{
  trip: TripSummary | null;
  position: TelemetryPosition | null;
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
        <div><dt>Chuyến đi</dt><dd>#{{ trip.id }}</dd></div>
        <div><dt>Tài xế</dt><dd>{{ trip.driver?.fullName ?? 'Chưa phân công' }}</dd></div>
        <div><dt>Khởi hành dự kiến</dt><dd>{{ displayTripTime(trip.scheduledDepartureAt) }}</dd></div>
      </dl>
      <p v-if="!position" class="tracking-vehicle-hint">
        Xe chưa có vị trí GPS. Biểu tượng trên bản đồ là vị trí trạm đầu theo kế hoạch,
        không phải vị trí thực tế của xe.
      </p>
      <p v-else class="tracking-vehicle-hint">
        Vị trí từ {{ position.source === 'SIMULATOR' ? 'mô phỏng' : 'GPS' }} ·
        cập nhật {{ displayTripTime(position.recordedAt) }}.
      </p>
      <RouterLink to="/trips" class="tracking-vehicle-link">Mở quản lý chuyến đi →</RouterLink>
    </template>
    <p v-else role="status">Chưa tải được thông tin chuyến đã chọn.</p>
    <button type="button" class="tracking-vehicle-clear" @click="onClear">Bỏ chọn xe</button>
  </div>
</template>
