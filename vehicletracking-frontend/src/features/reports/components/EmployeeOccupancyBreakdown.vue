<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { OperationalReportDetail } from '@/features/reports/types/reports';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import EmployeeOccupancyTripPanel from '@/features/reports/components/EmployeeOccupancyTripPanel.vue';

const props = defineProps<{ report: OperationalReportDetail }>();
type View = 'vehicle' | 'day' | 'station' | 'trip';
const views: { id: View; label: string }[] = [
  { id: 'vehicle', label: 'Theo xe' },
  { id: 'day', label: 'Theo ngày' },
  { id: 'station', label: 'Theo trạm' },
  { id: 'trip', label: 'Theo chuyến' },
];
const view = ref<View>('vehicle');
const page = ref(1);
const selectedTripId = ref<number | null>(null);
const pageSize = 10;
const vehicleRows = computed(() => props.report.employeeOccupancyByVehicle ?? []);
const dayRows = computed(() => props.report.employeeOccupancyByDay ?? []);
const stationRows = computed(() => props.report.employeeOccupancyByStation ?? []);
const tripRows = computed(() => props.report.employeeOccupancyTrips ?? []);
const count = computed(() => ({
  vehicle: vehicleRows.value.length,
  day: dayRows.value.length,
  station: stationRows.value.length,
  trip: tripRows.value.length,
})[view.value]);
const pageCount = computed(() => Math.max(1, Math.ceil(count.value / pageSize)));
const pageRows = <T,>(rows: T[]) => rows.slice((page.value - 1) * pageSize, page.value * pageSize);
const selectedTrip = computed(() => tripRows.value.find((trip) => trip.tripId === selectedTripId.value) ?? null);
const number = (value: number) => value.toLocaleString('vi-VN', { maximumFractionDigits: 2 });
const date = (value: string) => value.split('-').reverse().join('/');
const time = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
}).format(new Date(value));

watch(view, () => { page.value = 1; selectedTripId.value = null; });
watch(page, () => { selectedTripId.value = null; });
watch(() => props.report, () => { page.value = 1; selectedTripId.value = null; });
watch(count, () => { page.value = Math.min(page.value, pageCount.value); });
</script>

<template>
  <div class="report-occupancy-breakdown">
    <div class="report-occupancy-nav" aria-label="Cách xem thống kê hành khách">
      <button v-for="item in views" :key="item.id" type="button" :class="{ 'is-active': view === item.id }"
        :aria-pressed="view === item.id" @click="view = item.id">{{ item.label }}</button>
    </div>
    <p class="report-occupancy-context">Chỉ cộng lượt lên xe của các chuyến hoàn tất đã xác nhận đủ mọi trạm đón. Ngày tính theo giờ khởi hành dự kiến tại Việt Nam.</p>

    <template v-if="view === 'vehicle'">
      <div v-if="!vehicleRows.length" class="reports-empty">Chưa có chuyến hoàn tất trong kỳ báo cáo.</div>
      <div v-else class="report-table-scroll" role="region" aria-label="Bảng hành khách theo xe" tabindex="0">
        <table class="report-occupancy-table">
          <thead><tr><th scope="col">Xe</th><th scope="col">Số ghế hiện tại</th><th scope="col">Người trung bình/chuyến</th><th scope="col">Tỷ lệ sử dụng ghế/chuyến</th></tr></thead>
          <tbody><tr v-for="row in pageRows(vehicleRows)" :key="row.vehicleId ?? row.plateNumber ?? row.vehicleName ?? 'vehicle'">
            <td><strong>{{ row.plateNumber ?? 'Chưa có biển số' }}</strong><small>{{ row.vehicleName ?? 'Chưa có tên xe' }}</small></td>
            <td>{{ row.seatCapacity == null ? 'Chưa cấu hình' : number(row.seatCapacity) }}</td>
            <td>{{ row.averageBoardingsPerTrip == null ? 'Chưa xác nhận' : number(row.averageBoardingsPerTrip) }}</td>
            <td><div class="report-seat-usage"><span>{{ row.seatUtilizationPercent == null ? 'Chưa tính được' : `${number(row.seatUtilizationPercent)}%` }}</span><span v-if="row.seatUtilizationPercent != null" class="report-seat-track" aria-hidden="true"><span :style="{ width: `${Math.max(0, Math.min(100, row.seatUtilizationPercent))}%` }" /></span></div></td>
          </tr></tbody>
        </table>
      </div>
    </template>

    <template v-else-if="view === 'day'">
      <div v-if="!dayRows.length" class="reports-empty">Chưa có chuyến hoàn tất trong kỳ báo cáo.</div>
      <div v-else class="report-table-scroll" role="region" aria-label="Bảng hành khách theo ngày" tabindex="0">
        <table class="report-occupancy-detail-table">
          <thead><tr><th scope="col">Ngày chạy</th><th scope="col">Chuyến hoàn tất</th><th scope="col">Chuyến đã xác nhận</th><th scope="col">Lượt người được chở</th><th scope="col">Người trung bình/chuyến</th><th scope="col">Tỷ lệ sử dụng ghế</th></tr></thead>
          <tbody><tr v-for="row in pageRows(dayRows)" :key="row.date">
            <td><strong>{{ date(row.date) }}</strong></td><td>{{ number(row.completedTripCount) }}</td><td>{{ number(row.confirmedTripCount) }}</td>
            <td>{{ row.confirmedTripCount ? number(row.totalBoardings) : 'Chưa xác nhận' }}</td>
            <td>{{ row.averageBoardingsPerTrip == null ? 'Chưa xác nhận' : number(row.averageBoardingsPerTrip) }}</td>
            <td>{{ row.seatUtilizationPercent == null ? 'Chưa tính được' : `${number(row.seatUtilizationPercent)}%` }}</td>
          </tr></tbody>
        </table>
      </div>
    </template>

    <template v-else-if="view === 'station'">
      <div v-if="!stationRows.length" class="reports-empty">Chưa có số người được xác nhận đủ để thống kê theo trạm.</div>
      <div v-else class="report-table-scroll" role="region" aria-label="Bảng hành khách theo trạm" tabindex="0">
        <table class="report-occupancy-detail-table">
          <thead><tr><th scope="col">Trạm đón</th><th scope="col">Lượt xe ghé</th><th scope="col">Lượt người lên xe</th><th scope="col">Người trung bình/lượt ghé</th></tr></thead>
          <tbody><tr v-for="row in pageRows(stationRows)" :key="row.stationId ?? row.stationName ?? 'station'">
            <td><strong>{{ row.stationName ?? 'Chưa có tên trạm' }}</strong></td><td>{{ number(row.visitCount) }}</td>
            <td>{{ number(row.totalBoardings) }}</td><td>{{ row.averageBoardingsPerVisit == null ? 'Chưa tính được' : number(row.averageBoardingsPerVisit) }}</td>
          </tr></tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <div v-if="!tripRows.length" class="reports-empty">Chưa có chuyến hoàn tất trong kỳ báo cáo.</div>
      <div v-else class="report-table-scroll" role="region" aria-label="Bảng hành khách theo chuyến" tabindex="0">
        <table class="report-occupancy-detail-table">
          <thead><tr><th scope="col">Chuyến</th><th scope="col">Ngày · giờ dự kiến</th><th scope="col">Xe · số ghế lúc khởi hành</th><th scope="col">Tài xế</th><th scope="col">Người được chở</th><th scope="col">Chi tiết</th></tr></thead>
          <tbody><tr v-for="row in pageRows(tripRows)" :key="row.tripId">
            <td><strong>{{ row.routeName ?? 'Chưa có tên tuyến' }}</strong><small>Chuyến #{{ row.tripId }}</small></td>
            <td>{{ date(row.serviceDate) }} · {{ time(row.scheduledDepartureAt) }}</td>
            <td>{{ row.vehiclePlateNumber ?? 'Chưa có xe' }} · {{ row.seatCapacity == null ? 'Chưa biết số ghế' : `${number(row.seatCapacity)} ghế` }}</td>
            <td>{{ row.driverName ?? 'Chưa phân công' }}</td>
            <td>{{ row.complete ? number(row.totalBoardings ?? 0) : 'Chưa xác nhận đủ' }}</td>
            <td><button type="button" class="report-occupancy-detail-button" @click="selectedTripId = row.tripId">Xem trạm</button></td>
          </tr></tbody>
        </table>
      </div>
      <EmployeeOccupancyTripPanel v-if="selectedTrip" :trip="selectedTrip" @close="selectedTripId = null" />
    </template>

    <PaginationControls v-if="count" v-model:page="page" :page-count="pageCount" :total="count" :page-size="pageSize"
      :label="view === 'vehicle' ? 'xe' : view === 'day' ? 'ngày' : view === 'station' ? 'trạm' : 'chuyến'" />
  </div>
</template>
