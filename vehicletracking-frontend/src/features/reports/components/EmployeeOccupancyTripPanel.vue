<script setup lang="ts">
import { X } from '@lucide/vue';
import type { EmployeeOccupancyTripRow } from '@/features/reports/types/reports';
import SidePanel from '@/shared/components/SidePanel.vue';
import '@/features/reports/styles/employee-occupancy-trip.css';

defineProps<{ trip: EmployeeOccupancyTripRow }>();
const emit = defineEmits<{ close: [] }>();
const number = (value: number) => value.toLocaleString('vi-VN');
</script>

<template>
  <SidePanel class-name="employee-occupancy-trip" :label="`Số người lên ở các trạm của chuyến ${trip.tripId}`" :on-close="() => emit('close')">
    <header class="employee-occupancy-trip-heading">
      <div><p>NGƯỜI LÊN TỪNG TRẠM</p><h2>{{ trip.routeName ?? 'Chưa có tên tuyến' }}</h2>
        <span>Chuyến #{{ trip.tripId }} · {{ trip.vehiclePlateNumber ?? 'Chưa có xe' }}</span></div>
      <button type="button" aria-label="Đóng chi tiết trạm" @click="emit('close')"><X :size="19" aria-hidden="true" /></button>
    </header>
    <div class="employee-occupancy-trip-summary">
      <span>{{ trip.complete ? `${number(trip.totalBoardings ?? 0)} người được chở` : 'Chưa xác nhận đủ số người' }}</span>
      <span>{{ trip.seatCapacity == null ? 'Chưa biết số ghế lúc khởi hành' : `${number(trip.seatCapacity)} ghế lúc khởi hành` }}</span>
    </div>
    <div class="employee-occupancy-trip-body">
      <p>Hành khách chỉ lên tại trạm đón và cùng xuống ở điểm cuối. Khi một trạm chưa xác nhận, số cộng dồn ở các trạm sau chưa thể tính.</p>
      <div v-if="!trip.pickupStops.length" class="employee-occupancy-trip-empty">Chuyến chưa có trạm đón để đối chiếu.</div>
      <ol v-else class="employee-occupancy-stop-list">
        <li v-for="stop in trip.pickupStops" :key="stop.stopSequence">
          <div class="employee-occupancy-stop-name"><span>{{ stop.stopSequence }}</span><strong>{{ stop.stationName ?? 'Chưa có tên trạm' }}</strong></div>
          <dl><div><dt>Người lên tại trạm</dt><dd>{{ stop.boardingCount == null ? 'Chưa xác nhận' : number(stop.boardingCount) }}</dd></div>
            <div><dt>Trên xe sau trạm</dt><dd>{{ stop.onboardAfterStop == null ? 'Chưa rõ' : number(stop.onboardAfterStop) }}</dd></div></dl>
        </li>
      </ol>
    </div>
  </SidePanel>
</template>
