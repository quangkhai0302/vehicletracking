<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import { FileSpreadsheet, RefreshCw } from '@lucide/vue';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import type { Driver, FleetVehicle } from '@/features/fleet/types/fleet';
import { fetchOperationalReportDetail } from '@/features/reports/api/reports';
import type {
  OperationalReportDetail,
  OperationalReportFilters,
  OperationalReportIncidentRow,
} from '@/features/reports/types/reports';
import PageHeading from '@/shared/components/PageHeading.vue';
import AppDatePicker from '@/shared/components/AppDatePicker.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import '@/features/reports/styles/reports.css';

function dateInVietnam(offset: number) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date());
  const part = (type: string) => parts.find((entry) => entry.type === type)!.value;
  const date = new Date(`${part('year')}-${part('month')}-${part('day')}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}
type PeriodMode = 'MONTH' | 'YEAR' | 'CUSTOM';
function currentMonth() { return dateInVietnam(0).slice(0, 7); }
function monthRange(value: string) {
  const [year, month] = value.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return { from: `${value}-01`, to: `${value}-${String(lastDay).padStart(2, '0')}` };
}
function yearRange(value: string) { return { from: `${value}-01-01`, to: `${value}-12-31` }; }
function number(value: number) { return value.toLocaleString('vi-VN', { maximumFractionDigits: 2 }); }
function duration(seconds: number | null) {
  if (seconds == null) return 'Chưa có dữ liệu';
  const minutes = Math.floor(seconds / 60), rest = Math.floor(seconds % 60);
  if (minutes < 60) return `${minutes} phút ${rest} giây`;
  return `${Math.floor(minutes / 60)} giờ ${minutes % 60} phút`;
}
function timestamp(value: string | null) {
  if (!value) return 'Chưa có dữ liệu';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(value));
}
const incidentLabels: Record<string, string> = {
  OFF_ROUTE_DETECTED: 'Lệch tuyến', REROUTE_CREATED: 'Tạo tuyến thay thế',
  REROUTE_UNAVAILABLE: 'Không có tuyến thay thế', DRIVER_ROUTE_CHANGED: 'Tài xế đổi tuyến',
  DISPATCH_ATTENTION: 'Cần điều phối xử lý', DRIVER_UNAVAILABLE: 'Tài xế không khả dụng',
  DISPATCH_REASSIGNED: 'Điều phối lại tài xế', TRIP_AUTO_STARTED: 'Tự khởi hành',
  DIRECT_ASSIGNMENT_DECLINED: 'Từ chối nhận chuyến', OVERSPEED: 'Vượt tốc độ',
};

const periodMode = ref<PeriodMode>('MONTH');
const periodValue = ref(currentMonth());
const filters = shallowRef<OperationalReportFilters>(monthRange(currentMonth()));
const report = shallowRef<OperationalReportDetail | null>(null);
const vehicles = shallowRef<FleetVehicle[]>([]), drivers = shallowRef<Driver[]>([]);
const loading = ref(true), error = ref<string | null>(null), retry = ref(0);
const lateStopSearch = ref(''), lateStopStation = ref(''), lateStopVehicle = ref(''), lateStopDriver = ref(''), lateStopMinimumMinutes = ref('0');
const lateStopStations = computed(() => [...new Set((report.value?.lateStops ?? []).map((row) => row.stationName))].sort((a, b) => a.localeCompare(b, 'vi')));
const lateStopVehicles = computed(() => [...new Set((report.value?.lateStops ?? []).map((row) => row.vehiclePlateNumber ?? '__NO_VEHICLE__'))]
  .sort((a, b) => a.localeCompare(b, 'vi')));
const lateStopDrivers = computed(() => [...new Set((report.value?.lateStops ?? []).map((row) => row.driverName ?? '__NO_DRIVER__'))]
  .sort((a, b) => a.localeCompare(b, 'vi')));
const lateStopRows = computed(() => {
  const query = lateStopSearch.value.trim().toLocaleLowerCase('vi-VN');
  const minimumSeconds = Number(lateStopMinimumMinutes.value) * 60;
  return (report.value?.lateStops ?? []).filter((row) => {
    const matchesQuery = !query || [row.routeName, row.tripId, row.vehiclePlateNumber, row.driverName, row.stationName]
      .some((value) => String(value ?? '').toLocaleLowerCase('vi-VN').includes(query));
    const vehicleKey = row.vehiclePlateNumber ?? '__NO_VEHICLE__';
    const driverKey = row.driverName ?? '__NO_DRIVER__';
    return matchesQuery && (!lateStopStation.value || row.stationName === lateStopStation.value)
      && (!lateStopVehicle.value || vehicleKey === lateStopVehicle.value)
      && (!lateStopDriver.value || driverKey === lateStopDriver.value)
      && row.delaySeconds >= minimumSeconds;
  });
});
useErrorToast(error);
watch([filters, retry], (_, _previous, cleanup) => {
  const controller = new AbortController();
  cleanup(() => controller.abort());
  report.value = null; error.value = null;
  if (!filters.value.from || !filters.value.to) {
    loading.value = false; error.value = 'Hãy chọn đầy đủ ngày bắt đầu và ngày kết thúc.'; return;
  }
  loading.value = true;
  Promise.all([
    fetchOperationalReportDetail(filters.value, controller.signal),
    fetchFleetVehicles(controller.signal), fetchDrivers(controller.signal),
  ]).then(([result, fleet, people]) => {
    if (controller.signal.aborted) return;
    report.value = result; vehicles.value = fleet; drivers.value = people;
  }).catch((reason: unknown) => {
    if (!controller.signal.aborted) error.value = reason instanceof Error ? reason.message : 'Không thể tải báo cáo vận hành.';
  }).finally(() => { if (!controller.signal.aborted) loading.value = false; });
}, { immediate: true });
watch([periodMode, periodValue], ([mode, value]) => {
  if (mode === 'MONTH') {
    const month = /^\d{4}-\d{2}$/.test(value)
      ? value
      : `${value.slice(0, 4) || currentMonth().slice(0, 4)}-${currentMonth().slice(5)}`;
    if (month !== value) { periodValue.value = month; return; }
    applyFilters({ ...filters.value, ...monthRange(month) });
  } else if (mode === 'YEAR') {
    const year = /^\d{4}$/.test(value) ? value : value.slice(0, 4);
    if (/^\d{4}$/.test(year)) {
      if (year !== value) { periodValue.value = year; return; }
      applyFilters({ ...filters.value, ...yearRange(year) });
    }
  }
});
function applyFilters(next: OperationalReportFilters) { filters.value = { ...next }; }
function resetFilters() {
  periodMode.value = 'MONTH';
  periodValue.value = currentMonth();
  applyFilters({ ...filters.value, ...monthRange(currentMonth()), vehicleId: undefined, driverId: undefined });
}
function idFilter(key: 'vehicleId' | 'driverId', event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  applyFilters({ ...filters.value, [key]: value ? Number(value) : undefined });
}
function csvCell(value: unknown) {
  const text = value == null ? 'Chưa có dữ liệu' : String(value);
  return `"${text.replace(/"/g, '""')}"`;
}
function csvRow(values: unknown[]) { return values.map(csvCell).join(','); }
function incidentLabel(row: OperationalReportIncidentRow) { return incidentLabels[row.type] ?? row.type; }
function exportExcel() {
  if (!report.value) return;
  const r = report.value;
  const lines: string[] = [
    csvRow(['BÁO CÁO VẬN HÀNH', `${r.from} - ${r.to}`]),
    csvRow(['Phương tiện', filters.value.vehicleId ?? 'Tất cả']),
    csvRow(['Tài xế', filters.value.driverId ?? 'Tất cả']),
    csvRow(['Tổng lượt chạy', r.summary.tripCount, 'Đã hoàn thành', r.summary.completedTripCount]),
    csvRow(['Lượt trễ', r.summary.lateTripCount, 'Sự cố', r.incidents.reduce((sum, item) => sum + item.count, 0)]), '',
    csvRow(['THỐNG KÊ NGƯỜI TRÊN XE']),
    csvRow(['Định nghĩa', 'Người được chở trung bình/chuyến = tổng lượt người được chở tại các trạm đón / số chuyến hoàn tất đủ xác nhận tại mọi điểm đón.']),
    csvRow(['Định nghĩa', 'Người trung bình trên xe = tổng người-giây / thời gian từ check-in điểm này đến check-in điểm kế tiếp. Với mô phỏng dùng thời gian mô phỏng; nếu thiếu, dùng giờ đến thực tế.']),
    csvRow(['Định nghĩa', 'Sử dụng ghế = tổng người-giây / tổng (số ghế × thời gian giữa hai lần xác nhận liên tiếp tại trạm đón) trên các chặng đã xác nhận số người và xe có cấu hình sức chứa.']),
    csvRow(['Tổng lượt người được chở', r.employeeOccupancy.totalBoardings,
      'Người được chở trung bình/chuyến', r.employeeOccupancy.averageBoardingsPerTrip,
      'Người trung bình trên xe', r.employeeOccupancy.averageOnboard,
      'Tỷ lệ sử dụng ghế (%)', r.employeeOccupancy.seatUtilizationPercent]),
    csvRow(['Chuyến đã xác nhận số người tại tất cả trạm đón', r.employeeOccupancy.tripsWithCompleteBoardingData,
      'Chuyến còn thiếu xác nhận tại trạm đón', r.employeeOccupancy.tripsMissingBoardingData,
      'Thiếu sức chứa xe', r.employeeOccupancy.tripsMissingSeatCapacity]), '',
    csvRow(['THEO XE · SỐ NGƯỜI VÀ SỬ DỤNG GHẾ']),
    csvRow(['Xe', 'Sức chứa ghế', 'Chuyến còn thiếu xác nhận', 'Người được chở/chuyến', 'Người trung bình trên xe', 'Sử dụng ghế (%)']),
    ...r.employeeOccupancyByVehicle.map((row) => csvRow([
      [row.plateNumber, row.vehicleName].filter(Boolean).join(' · '), row.seatCapacity,
      row.tripsMissingBoardingData,
      row.averageBoardingsPerTrip, row.averageOnboard, row.seatUtilizationPercent,
    ])), '',
    csvRow(['THEO XE']), csvRow(['Xe', 'Số lượt', 'Hoàn thành', 'Lượt trễ', 'Trễ trạm', 'Sự cố', 'Nhân viên đi xe']),
    ...r.vehicles.map((row) => csvRow([[row.plateNumber, row.vehicleName].filter(Boolean).join(' · '), row.tripCount, row.completedTripCount, row.lateTripCount, row.lateStopCount, row.incidentCount, row.employeePassengerCount])), '',
    csvRow(['THEO TÀI XẾ']), csvRow(['Tài xế', 'Số lượt', 'Hoàn thành', 'Lượt trễ', 'Trễ trạm', 'Sự cố', 'Nhân viên đi xe']),
    ...r.drivers.map((row) => csvRow([row.driverName ?? 'Chưa phân công', row.tripCount, row.completedTripCount, row.lateTripCount, row.lateStopCount, row.incidentCount, row.employeePassengerCount])), '',
    csvRow(['CÁC LẦN TRỄ TRẠM']), csvRow(['Mã chuyến', 'Tên tuyến', 'Xe', 'Tài xế', 'Trạm', 'Thứ tự', 'Dự kiến', 'Thực tế', 'Trễ']),
    ...lateStopRows.value.map((row) => csvRow([row.tripId, row.routeName, row.vehiclePlateNumber, row.driverName, row.stationName, row.stopSequence, timestamp(row.plannedArrivalAt), timestamp(row.actualArrivalAt), duration(row.delaySeconds)])), '',
    csvRow(['SỰ CỐ']), csvRow(['Loại', 'Mức độ', 'Số lần']),
    ...r.incidents.map((row) => csvRow([incidentLabel(row), row.severity, row.count])), '',
    csvRow(['Ghi chú', r.employeePassengerDataNote]),
  ];
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  link.href = url; link.download = `bao-cao-van-hanh-${r.from}-${r.to}.csv`; link.click(); URL.revokeObjectURL(url);
}
</script>

<template>
  <div class="business-page reports-page">
    <PageHeading eyebrow="PHÂN TÍCH VẬN HÀNH" title="Báo cáo và thống kê" description="Theo dõi số lượt chạy theo xe, tài xế, trễ trạm và sự cố trong khoảng thời gian đã chọn.">
      <template #actions>
        <button type="button" class="business-button reports-refresh" :disabled="loading" @click="retry++"><RefreshCw :size="16" /> Làm mới</button>
        <button type="button" class="business-button reports-export" :disabled="!report || loading" @click="exportExcel"><FileSpreadsheet :size="16" /> Xuất Excel</button>
      </template>
    </PageHeading>
    <section class="business-surface reports-filter-panel" aria-label="Bộ lọc báo cáo">
      <div class="reports-filter-heading"><div><h2>Phạm vi báo cáo</h2><p>Ngày khởi hành dự kiến theo giờ Việt Nam; số liệu được tổng hợp từ các chuyến đã ghi nhận.</p></div><button type="button" class="reports-reset" @click="resetFilters">Xóa bộ lọc</button></div>
      <div class="reports-filters">
        <label><span>Kỳ báo cáo</span><select v-model="periodMode" aria-label="Kỳ báo cáo"><option value="MONTH">Theo tháng</option><option value="YEAR">Theo năm</option><option value="CUSTOM">Khoảng ngày</option></select></label>
        <label v-if="periodMode === 'MONTH'"><span>Tháng</span><input v-model="periodValue" type="month" aria-label="Tháng báo cáo" /></label>
        <label v-else-if="periodMode === 'YEAR'"><span>Năm</span><input v-model="periodValue" type="number" min="2000" max="2100" step="1" aria-label="Năm báo cáo" /></label>
        <label v-else><span>Từ ngày</span><AppDatePicker required :model-value="filters.from" :max="filters.to" placeholder="Từ ngày" @update:model-value="applyFilters({ ...filters, from: $event })" /></label>
        <label v-if="periodMode === 'CUSTOM'"><span>Đến ngày</span><AppDatePicker required :model-value="filters.to" :min="filters.from" placeholder="Đến ngày" @update:model-value="applyFilters({ ...filters, to: $event })" /></label>
        <label><span>Phương tiện</span><select :value="filters.vehicleId ?? ''" @change="idFilter('vehicleId', $event)"><option value="">Tất cả phương tiện</option><option v-for="vehicle in vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNumber }} · {{ vehicle.name }}</option></select></label>
        <label><span>Tài xế</span><select :value="filters.driverId ?? ''" @change="idFilter('driverId', $event)"><option value="">Tất cả tài xế</option><option v-for="driver in drivers" :key="driver.id" :value="driver.id">{{ driver.fullName }} · {{ driver.licenseNumber }}</option></select></label>
      </div>
    </section>
    <div v-if="loading" class="reports-loading" role="status">Đang tải báo cáo vận hành…</div>
    <div v-else-if="error" class="simulation-report-error" role="alert"><p>{{ error }}</p><button type="button" class="business-button" @click="retry++">Thử lại</button></div>
    <template v-else-if="report">
      <section class="reports-summary-strip" aria-label="Tổng quan báo cáo"><div><span>Tổng lượt chạy</span><strong>{{ number(report.summary.tripCount) }}</strong></div><div><span>Đã hoàn thành</span><strong>{{ number(report.summary.completedTripCount) }}</strong></div><div><span>Lượt trễ</span><strong>{{ number(report.summary.lateTripCount) }}</strong></div><div><span>Trễ trạm</span><strong>{{ number(report.lateStops.length) }}</strong></div><div><span>Sự cố</span><strong>{{ number(report.incidents.reduce((sum, item) => sum + item.count, 0)) }}</strong></div></section>
      <p class="reports-data-note">{{ report.employeePassengerDataNote }}</p>
      <section class="business-surface report-table-card report-occupancy-card" aria-label="Thống kê người trên xe">
        <div class="report-section-heading"><div><h2>Hành khách và mức sử dụng ghế</h2><p>Tài xế xác nhận số người lên tại các trạm đón được cấu hình trên tuyến; hành khách xuống tại điểm cuối.</p></div></div>
        <div class="reports-occupancy-metrics">
          <div><span>Tổng lượt người được chở</span><strong>{{ number(report.employeeOccupancy.totalBoardings) }}</strong><small>Cộng qua các chuyến trong kỳ báo cáo.</small></div>
          <div><span>Người được chở TB/chuyến</span><strong>{{ report.employeeOccupancy.averageBoardingsPerTrip == null ? 'Chưa có dữ liệu' : number(report.employeeOccupancy.averageBoardingsPerTrip) }}</strong><small>Tổng lượt người được chở chia cho số chuyến đã xác nhận số người tại tất cả trạm đón.</small></div>
          <div><span>Người trên xe TB</span><strong>{{ report.employeeOccupancy.averageOnboard == null ? 'Chưa có dữ liệu' : number(report.employeeOccupancy.averageOnboard) }}</strong><small>Bình quân có tính thời lượng từng chặng.</small></div>
          <div><span>Ghế lấp đầy TB</span><strong>{{ report.employeeOccupancy.seatUtilizationPercent == null ? 'Chưa có dữ liệu' : `${number(report.employeeOccupancy.seatUtilizationPercent)}%` }}</strong><small>Tỷ lệ ghế có người, tính theo thời gian.</small></div>
        </div>
        <p class="occupancy-method-note">Số người được chở được cộng từ lượt lên tại các trạm đón đã cấu hình trên tuyến; không tính điểm đón ngoài tuyến và hành khách xuống ở điểm cuối. Tổng số cộng dồn qua các chuyến trong kỳ, không phải số người duy nhất. Chỉ tính chuyến hoàn tất có xác nhận ở tất cả điểm đón. Người trung bình trên xe còn tùy thời lượng từng chặng.</p>
        <p class="reports-data-note">{{ number(report.employeeOccupancy.tripsWithCompleteBoardingData) }} chuyến đã xác nhận số người tại tất cả trạm đón · {{ number(report.employeeOccupancy.tripsMissingBoardingData) }} chuyến còn thiếu xác nhận ở trạm đón · {{ number(report.employeeOccupancy.tripsMissingSeatCapacity) }} xe chưa cấu hình số ghế.</p>
        <div v-if="!report.employeeOccupancyByVehicle.length" class="reports-empty">Chưa có chuyến hoàn tất đã xác nhận số người tại tất cả trạm đón để thống kê người trên xe.</div>
        <div v-else class="report-table-scroll">
          <table>
            <thead>
              <tr>
                <th>Xe<small>Biển số · tên xe</small></th>
                <th>Số ghế<small>Sức chứa đã cấu hình</small></th>
                <th>Người được chở/chuyến<small>Tổng lượt người / chuyến đã xác nhận đủ các trạm đón</small></th>
                <th>Người trên xe TB<small>Bình quân theo thời lượng chặng</small></th>
                <th>Ghế lấp đầy TB<small>Tỷ lệ ghế có người theo thời gian</small></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in report.employeeOccupancyByVehicle" :key="row.vehicleId ?? row.plateNumber ?? row.vehicleName ?? 'vehicle'">
                <td><strong>{{ row.plateNumber ?? 'Chưa có biển số' }}</strong><small>{{ row.vehicleName ?? 'Chưa có tên xe' }}</small></td>
                <td>{{ row.seatCapacity == null ? 'Chưa cấu hình' : number(row.seatCapacity) }}</td>
                <td>{{ row.averageBoardingsPerTrip == null ? 'Chưa có dữ liệu' : number(row.averageBoardingsPerTrip) }}</td>
                <td>{{ row.averageOnboard == null ? 'Chưa có dữ liệu' : number(row.averageOnboard) }}</td>
                <td>{{ row.seatUtilizationPercent == null ? 'Chưa có dữ liệu' : `${number(row.seatUtilizationPercent)}%` }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="business-surface report-table-card" aria-label="Thống kê theo xe"><div class="report-section-heading"><div><h2>Số lượt theo xe</h2><p>Mỗi chuyến được tính theo xe được gán tại thời điểm khởi hành.</p></div></div><div v-if="!report.vehicles.length" class="reports-empty">Không có dữ liệu phù hợp.</div><div v-else class="report-table-scroll"><table><thead><tr><th>Xe</th><th>Số lượt</th><th>Hoàn thành</th><th>Lượt trễ</th><th>Trễ trạm</th><th>Sự cố</th><th>Nhân viên đi xe</th></tr></thead><tbody><tr v-for="row in report.vehicles" :key="row.vehicleId ?? row.plateNumber ?? row.vehicleName ?? 'vehicle'"><td><strong>{{ row.plateNumber ?? 'Chưa có biển số' }}</strong><small>{{ row.vehicleName ?? 'Chưa có tên xe' }}</small></td><td>{{ number(row.tripCount) }}</td><td>{{ number(row.completedTripCount) }}</td><td>{{ number(row.lateTripCount) }}</td><td>{{ number(row.lateStopCount) }}</td><td>{{ number(row.incidentCount) }}</td><td>{{ row.employeePassengerCount == null ? 'Chưa có dữ liệu' : number(row.employeePassengerCount) }}</td></tr></tbody></table></div></section>
      <section class="business-surface report-table-card" aria-label="Thống kê theo tài xế"><div class="report-section-heading"><div><h2>Số lượt theo tài xế</h2><p>Chuyến chưa gán tài xế được gom vào nhóm chưa phân công.</p></div></div><div v-if="!report.drivers.length" class="reports-empty">Không có dữ liệu phù hợp.</div><div v-else class="report-table-scroll"><table><thead><tr><th>Tài xế</th><th>Số lượt</th><th>Hoàn thành</th><th>Lượt trễ</th><th>Trễ trạm</th><th>Sự cố</th><th>Nhân viên đi xe</th></tr></thead><tbody><tr v-for="row in report.drivers" :key="row.driverId ?? row.driverName ?? 'driver'"><td><strong>{{ row.driverName ?? 'Chưa phân công' }}</strong></td><td>{{ number(row.tripCount) }}</td><td>{{ number(row.completedTripCount) }}</td><td>{{ number(row.lateTripCount) }}</td><td>{{ number(row.lateStopCount) }}</td><td>{{ number(row.incidentCount) }}</td><td>{{ row.employeePassengerCount == null ? 'Chưa có dữ liệu' : number(row.employeePassengerCount) }}</td></tr></tbody></table></div></section>
      <section class="reports-detail-grid">
        <section class="business-surface report-table-card" aria-label="Các lần trễ trạm">
          <div class="report-section-heading">
            <div>
              <h2>Các lần trễ trạm</h2>
              <p>So sánh giờ đến thực tế với giờ đến dự kiến ban đầu.</p>
            </div>
          </div>
          <div v-if="report.lateStops.length" class="late-stop-filters" aria-label="Bộ lọc trễ trạm">
            <label>
              <span>Tìm chuyến, xe, tài xế hoặc trạm</span>
              <input v-model="lateStopSearch" type="search" placeholder="Nhập thông tin cần tìm" />
            </label>
            <label>
              <span>Trạm</span>
              <select v-model="lateStopStation">
                <option value="">Tất cả trạm</option>
                <option v-for="station in lateStopStations" :key="station" :value="station">{{ station }}</option>
              </select>
            </label>
            <label>
              <span>Xe</span>
              <select v-model="lateStopVehicle">
                <option value="">Tất cả xe</option>
                <option v-for="vehicle in lateStopVehicles" :key="vehicle" :value="vehicle">
                  {{ vehicle === '__NO_VEHICLE__' ? 'Chưa có xe' : vehicle }}
                </option>
              </select>
            </label>
            <label>
              <span>Tài xế</span>
              <select v-model="lateStopDriver">
                <option value="">Tất cả tài xế</option>
                <option v-for="driver in lateStopDrivers" :key="driver" :value="driver">
                  {{ driver === '__NO_DRIVER__' ? 'Chưa phân công' : driver }}
                </option>
              </select>
            </label>
            <label>
              <span>Trễ tối thiểu</span>
              <select v-model="lateStopMinimumMinutes">
                <option value="0">Tất cả mức trễ</option>
                <option value="5">Từ 5 phút</option>
                <option value="15">Từ 15 phút</option>
                <option value="30">Từ 30 phút</option>
              </select>
            </label>
            <button
              type="button"
              class="business-button"
              :disabled="!lateStopSearch && !lateStopStation && !lateStopVehicle && !lateStopDriver && lateStopMinimumMinutes === '0'"
              @click="lateStopSearch = ''; lateStopStation = ''; lateStopVehicle = ''; lateStopDriver = ''; lateStopMinimumMinutes = '0'"
            >Xóa lọc trễ trạm</button>
          </div>
          <div v-if="!report.lateStops.length" class="reports-empty">Không ghi nhận trễ trạm.</div>
          <div v-else-if="!lateStopRows.length" class="reports-empty">Không có lần trễ trạm nào khớp bộ lọc.</div>
          <div v-else>
            <p class="late-stop-result-count">Hiển thị {{ lateStopRows.length }} / {{ report.lateStops.length }} lần trễ trạm</p>
            <div class="report-table-scroll">
              <table>
                <thead><tr><th>Chuyến · Xe</th><th>Trạm</th><th>Dự kiến</th><th>Thực tế</th><th>Trễ</th></tr></thead>
                <tbody>
                  <tr v-for="row in lateStopRows" :key="`${row.tripId}-${row.stopSequence}`">
                    <td><strong>{{ row.routeName ?? 'Chưa có tên tuyến' }}</strong><small>Chuyến #{{ row.tripId }} · {{ row.vehiclePlateNumber ?? 'Chưa có xe' }}</small><small>{{ row.driverName ?? 'Chưa phân công' }}</small></td>
                    <td>{{ row.stopSequence }}. {{ row.stationName }}</td>
                    <td>{{ timestamp(row.plannedArrivalAt) }}</td>
                    <td>{{ timestamp(row.actualArrivalAt) }}</td>
                    <td class="report-danger">{{ duration(row.delaySeconds) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
        <section class="business-surface report-table-card" aria-label="Sự cố">
          <div class="report-section-heading"><div><h2>Sự cố</h2><p>Lệch tuyến, vượt tốc độ và các cảnh báo điều phối đã ghi nhận.</p></div></div>
          <div v-if="!report.incidents.length" class="reports-empty">Không ghi nhận sự cố.</div>
          <div v-else class="report-table-scroll">
            <table>
              <thead><tr><th>Loại</th><th>Mức độ</th><th>Số lần</th></tr></thead>
              <tbody><tr v-for="row in report.incidents" :key="`${row.type}-${row.severity}`"><td>{{ incidentLabel(row) }}</td><td>{{ row.severity }}</td><td>{{ number(row.count) }}</td></tr></tbody>
            </table>
          </div>
        </section>
      </section>
    </template>
  </div>
</template>
