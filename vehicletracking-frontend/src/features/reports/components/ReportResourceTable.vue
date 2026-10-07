<script setup lang="ts">
import { ChevronRight } from '@lucide/vue';
import type { OperationalReportDriverRow, OperationalReportVehicleRow } from '@/features/reports/types/reports';

defineProps<{ rows: (OperationalReportVehicleRow | OperationalReportDriverRow)[]; resource: 'vehicle' | 'driver' }>();
const emit = defineEmits<{ viewDriverTrips: [driver: OperationalReportDriverRow] }>();
const number = (value: number) => value.toLocaleString('vi-VN');
const rowKey = (row: OperationalReportVehicleRow | OperationalReportDriverRow) => 'vehicleId' in row
  ? `vehicle-${row.vehicleId ?? row.plateNumber ?? row.vehicleName}` : `driver-${row.driverId ?? row.driverName}`;
</script>

<template>
  <table class="report-resource-table">
    <thead><tr>
      <th scope="col">{{ resource === 'vehicle' ? 'Xe' : 'Tài xế' }}</th>
      <th scope="col">Chuyến đã thực hiện</th>
      <th scope="col">Chuyến đã hoàn tất</th>
      <th scope="col">Chuyến quá thời gian dự kiến</th>
      <th scope="col">Số lần đến trạm trễ</th>
      <th scope="col">Sự cố / cảnh báo</th>
      <th scope="col">Số lượng nhân viên đi xe</th>
    </tr></thead>
    <tbody><tr v-for="row in rows" :key="rowKey(row)">
      <td v-if="'vehicleId' in row"><strong>{{ row.plateNumber ?? 'Chưa có biển số' }}</strong><small>{{ row.vehicleName ?? 'Chưa có tên xe' }}</small></td>
      <td v-else>
        <strong>{{ row.driverName ?? 'Chưa phân công' }}</strong>
        <button type="button" class="report-driver-trips-button" aria-haspopup="dialog" :aria-label="`Xem chuyến của ${row.driverName ?? 'tài xế chưa xác định'}`" @click="emit('viewDriverTrips', row)">Xem chuyến <ChevronRight :size="13" aria-hidden="true" /></button>
      </td>
      <td><b class="report-trip-count">{{ number(row.tripCount) }}</b></td>
      <td>{{ number(row.completedTripCount) }}</td>
      <td><span class="report-count" :class="{ 'is-late': row.lateTripCount > 0 }">{{ number(row.lateTripCount) }}</span></td>
      <td><span class="report-count" :class="{ 'is-late': row.lateStopCount > 0 }">{{ number(row.lateStopCount) }}</span></td>
      <td><span class="report-count" :class="{ 'is-incident': row.incidentCount > 0 }">{{ number(row.incidentCount) }}</span></td>
      <td><span :class="{ 'report-unconfirmed': row.employeePassengerCount == null }">{{ row.employeePassengerCount == null ? 'Chưa xác nhận' : number(row.employeePassengerCount) }}</span></td>
    </tr></tbody>
  </table>
</template>
