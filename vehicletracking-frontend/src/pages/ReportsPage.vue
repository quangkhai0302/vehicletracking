<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, watch } from 'vue';
import { CalendarDays, ChevronDown, FileSpreadsheet, Info, RefreshCw, SlidersHorizontal } from '@lucide/vue';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import type { Driver, FleetVehicle } from '@/features/fleet/types/fleet';
import { fetchOperationalReportDetail } from '@/features/reports/api/reports';
import { buildReportWorkbook } from '@/features/reports/utils/reportExcel';
import { incidentLabel, severityLabel, statusLabel } from '@/features/reports/utils/reportLabels';
import type {
  OperationalReportDetail,
  OperationalReportFilters,
  OperationalReportDriverRow,
} from '@/features/reports/types/reports';
import type { ReportSection } from '@/features/reports/types/reportWorkspace';
import ReportOverview from '@/features/reports/components/ReportOverview.vue';
import ReportSectionTabs from '@/features/reports/components/ReportSectionTabs.vue';
import ReportResourceTable from '@/features/reports/components/ReportResourceTable.vue';
import DriverReportTripsPanel from '@/features/reports/components/DriverReportTripsPanel.vue';
import EmployeeOccupancyBreakdown from '@/features/reports/components/EmployeeOccupancyBreakdown.vue';
import PageHeading from '@/shared/components/PageHeading.vue';
import AppDatePicker from '@/shared/components/AppDatePicker.vue';
import PaginationControls from '@/shared/components/PaginationControls.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import '@/features/reports/styles/reports.css';
import '@/features/reports/styles/report-workspace.css';

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
const periodMode = ref<PeriodMode>('MONTH');
const reportYear = ref(currentMonth().slice(0, 4));
const reportMonth = ref(currentMonth().slice(5));
const months = Array.from({ length: 12 }, (_, i) => ({ value: String(i + 1).padStart(2, '0'), label: `Tháng ${i + 1}` }));
const filters = shallowRef<OperationalReportFilters>(monthRange(currentMonth()));
const filtersExpanded = ref(false);
const activeSection = ref<ReportSection>('vehicles');
const dateLabel = (value: string) => value ? value.split('-').reverse().join('/') : 'Chưa chọn ngày';
const periodLabel = computed(() => {
  if (periodMode.value === 'MONTH') return `Tháng ${Number(filters.value.from.slice(5, 7))}, ${filters.value.from.slice(0, 4)}`;
  if (periodMode.value === 'YEAR') return `Năm ${filters.value.from.slice(0, 4)}`;
  return 'Khoảng ngày tùy chọn';
});
const report = shallowRef<OperationalReportDetail | null>(null);
const selectedDriverReport = shallowRef<OperationalReportDriverRow | null>(null);
const vehicles = shallowRef<FleetVehicle[]>([]), drivers = shallowRef<Driver[]>([]);
const selectedVehicle = computed(() => filters.value.vehicleId
  ? vehicles.value.find((vehicle) => vehicle.id === filters.value.vehicleId)?.plateNumber ?? `Xe #${filters.value.vehicleId}` : 'Tất cả xe');
const selectedDriver = computed(() => filters.value.driverId
  ? drivers.value.find((driver) => driver.id === filters.value.driverId)?.fullName ?? `Tài xế #${filters.value.driverId}` : 'Tất cả tài xế');
async function showSection(section: ReportSection) {
  activeSection.value = section;
  await nextTick();
  const tab = document.getElementById(`report-tab-${section}`);
  tab?.focus({ preventScroll: true });
  tab?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
}
const loading = ref(true), error = ref<string | null>(null), retry = ref(0);
const exporting = ref(false), exportError = ref<string | null>(null);
const lateStopSearch = ref(''), lateStopStation = ref(''), lateStopVehicle = ref(''), lateStopDriver = ref(''), lateStopMinimumMinutes = ref('0');
const hasLateStopFilters = computed(() => Boolean(lateStopSearch.value || lateStopStation.value || lateStopVehicle.value || lateStopDriver.value || lateStopMinimumMinutes.value !== '0'));
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
const reportPageSize = 10;
const vehiclePage = ref(1), driverPage = ref(1), lateStopPage = ref(1), incidentPage = ref(1), incidentDetailPage = ref(1);
const vehicleRows = computed(() => report.value?.vehicles ?? []);
const driverRows = computed(() => report.value?.drivers ?? []);
const incidentRows = computed(() => report.value?.incidents ?? []);
const incidentDetailRows = computed(() => report.value?.incidentDetails ?? []);
const pageCount = (total: number) => Math.max(1, Math.ceil(total / reportPageSize));
const pageRows = <T,>(rows: T[], page: number) => rows.slice((page - 1) * reportPageSize, page * reportPageSize);
const pageVehicleRows = computed(() => pageRows(vehicleRows.value, vehiclePage.value));
const pageDriverRows = computed(() => pageRows(driverRows.value, driverPage.value));
const pageLateStopRows = computed(() => pageRows(lateStopRows.value, lateStopPage.value));
const pageIncidentRows = computed(() => pageRows(incidentRows.value, incidentPage.value));
const pageIncidentDetails = computed(() => pageRows(incidentDetailRows.value, incidentDetailPage.value));
watch([lateStopSearch, lateStopStation, lateStopVehicle, lateStopDriver, lateStopMinimumMinutes], () => (lateStopPage.value = 1));
watch(() => [vehicleRows.value.length, driverRows.value.length, lateStopRows.value.length, incidentRows.value.length, incidentDetailRows.value.length], ([vehiclesCount, driversCount, lateStops, incidents, details]) => {
  vehiclePage.value = Math.min(vehiclePage.value, pageCount(vehiclesCount));
  driverPage.value = Math.min(driverPage.value, pageCount(driversCount));
  lateStopPage.value = Math.min(lateStopPage.value, pageCount(lateStops));
  incidentPage.value = Math.min(incidentPage.value, pageCount(incidents));
  incidentDetailPage.value = Math.min(incidentDetailPage.value, pageCount(details));
});
useErrorToast(error);
useErrorToast(exportError);
watch([filters, retry], (_, _previous, cleanup) => {
  const controller = new AbortController();
  cleanup(() => controller.abort());
  report.value = null; error.value = null;
  selectedDriverReport.value = null;
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
watch([periodMode, reportYear, reportMonth], ([mode, year, month]) => {
  if (!/^\d{4}$/.test(year) || Number(year) < 2000 || Number(year) > 2100) return;
  if (mode === 'MONTH') applyFilters({ ...filters.value, ...monthRange(`${year}-${month}`) });
  else if (mode === 'YEAR') applyFilters({ ...filters.value, ...yearRange(year) });
});
function applyFilters(next: OperationalReportFilters) { filters.value = { ...next }; }
function resetFilters() {
  periodMode.value = 'MONTH';
  reportYear.value = currentMonth().slice(0, 4);
  reportMonth.value = currentMonth().slice(5);
  applyFilters({ ...filters.value, ...monthRange(currentMonth()), vehicleId: undefined, driverId: undefined });
}
function idFilter(key: 'vehicleId' | 'driverId', event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  applyFilters({ ...filters.value, [key]: value ? Number(value) : undefined });
}
async function exportReportExcel() {
  if (!report.value || exporting.value) return;
  const currentReport = report.value;
  exporting.value = true;
  exportError.value = null;
  try {
    const { sheets, options } = buildReportWorkbook(currentReport, {
      vehicle: selectedVehicle.value,
      driver: selectedDriver.value,
      lateStops: lateStopRows.value,
      lateStopFilterDescription: `Tìm: ${lateStopSearch.value.trim() || 'Không lọc'}; Trạm: ${lateStopStation.value || 'Tất cả'}; Xe: ${lateStopVehicle.value === '__NO_VEHICLE__' ? 'Chưa có xe' : lateStopVehicle.value || 'Tất cả'}; Tài xế: ${lateStopDriver.value === '__NO_DRIVER__' ? 'Chưa phân công' : lateStopDriver.value || 'Tất cả'}; Trễ tối thiểu: ${lateStopMinimumMinutes.value} phút`,
    });
    const { default: writeExcelFile } = await import('write-excel-file/browser');
    await writeExcelFile(sheets, options).toFile(`bao-cao-van-hanh-${currentReport.from}-${currentReport.to}.xlsx`);
  } catch {
    exportError.value = 'Không thể tạo file Excel. Vui lòng thử lại.';
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <div class="business-page reports-page">
    <PageHeading eyebrow="BÁO CÁO VẬN HÀNH" title="Báo cáo và thống kê" description="Xem kết quả trong kỳ và chọn từng mục để tìm hiểu chi tiết.">
      <template #actions>
        <button type="button" class="business-button reports-refresh" :disabled="loading" @click="retry++"><RefreshCw :size="16" /> Làm mới</button>
        <button type="button" class="business-button reports-export" title="Tải toàn bộ báo cáo thành file Excel, không chỉ mục đang xem" :disabled="!report || loading || exporting" :aria-busy="exporting" @click="exportReportExcel"><FileSpreadsheet :size="16" /> {{ exporting ? 'Đang tạo Excel…' : 'Xuất Excel' }}</button>
      </template>
    </PageHeading>
    <section class="business-surface reports-filter-panel" aria-label="Bộ lọc báo cáo">
      <div class="reports-filter-heading">
        <div class="report-period-summary" title="Ngày theo giờ khởi hành dự kiến, múi giờ Việt Nam">
          <span class="report-period-icon"><CalendarDays :size="21" aria-hidden="true" /></span>
          <div><h2>{{ periodLabel }}</h2><p>{{ dateLabel(filters.from) }} – {{ dateLabel(filters.to) }}</p></div>
        </div>
        <div class="report-scope-chips" :class="{ 'is-default': !filters.vehicleId && !filters.driverId }" aria-label="Phạm vi đang xem">
          <span v-if="!filters.vehicleId && !filters.driverId">Toàn bộ xe và tài xế</span>
          <span v-if="filters.vehicleId">Xe: {{ selectedVehicle }}</span>
          <span v-if="filters.driverId">Tài xế: {{ selectedDriver }}</span>
        </div>
        <button type="button" class="business-button report-filter-toggle" :aria-expanded="filtersExpanded" aria-controls="report-filter-fields" @click="filtersExpanded = !filtersExpanded">
          <SlidersHorizontal :size="16" aria-hidden="true" /> Bộ lọc <ChevronDown :size="15" aria-hidden="true" :class="{ 'is-expanded': filtersExpanded }" />
        </button>
      </div>
      <div v-show="filtersExpanded" id="report-filter-fields">
      <div class="reports-filters">
        <label><span>Kỳ báo cáo</span><select v-model="periodMode" aria-label="Kỳ báo cáo"><option value="MONTH">Theo tháng</option><option value="YEAR">Theo năm</option><option value="CUSTOM">Khoảng ngày</option></select></label>
        <label v-if="periodMode === 'MONTH'"><span>Tháng</span><select v-model="reportMonth" aria-label="Tháng báo cáo"><option v-for="month in months" :key="month.value" :value="month.value">{{ month.label }}</option></select></label>
        <label v-if="periodMode !== 'CUSTOM'"><span>Năm</span><input v-model="reportYear" type="number" min="2000" max="2100" step="1" aria-label="Năm báo cáo" /></label>
        <label v-if="periodMode === 'CUSTOM'"><span>Từ ngày</span><AppDatePicker required :model-value="filters.from" :max="filters.to" placeholder="Từ ngày" @update:model-value="applyFilters({ ...filters, from: $event })" /></label>
        <label v-if="periodMode === 'CUSTOM'"><span>Đến ngày</span><AppDatePicker required :model-value="filters.to" :min="filters.from" placeholder="Đến ngày" @update:model-value="applyFilters({ ...filters, to: $event })" /></label>
        <label><span>Phương tiện</span><select aria-label="Phương tiện báo cáo" :value="filters.vehicleId ?? ''" @change="idFilter('vehicleId', $event)"><option value="">Tất cả phương tiện</option><option v-for="vehicle in vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.plateNumber }} · {{ vehicle.name }}</option></select></label>
        <label><span>Tài xế</span><select aria-label="Tài xế báo cáo" :value="filters.driverId ?? ''" @change="idFilter('driverId', $event)"><option value="">Tất cả tài xế</option><option v-for="driver in drivers" :key="driver.id" :value="driver.id">{{ driver.fullName }} · {{ driver.licenseNumber }}</option></select></label>
      </div>
      <div class="report-filter-footer"><span>Áp dụng cho tất cả các mục báo cáo.</span><button type="button" class="reports-reset" @click="resetFilters">Xóa bộ lọc</button></div>
      </div>
    </section>
    <p v-if="hasLateStopFilters" class="report-export-hint"><Info :size="15" aria-hidden="true" /> File Excel sẽ áp dụng bộ lọc riêng của trễ trạm. <button type="button" :disabled="!report" @click="showSection('late-stops')">Xem bộ lọc</button></p>
    <div v-if="loading" class="reports-loading" role="status"><RefreshCw :size="18" aria-hidden="true" /> Đang tải báo cáo vận hành…</div>
    <div v-else-if="error" class="simulation-report-error" role="alert"><p>{{ error }}</p><button type="button" class="business-button" @click="retry++">Thử lại</button></div>
    <template v-else-if="report">
      <!-- <div class="report-overview-heading"><span>Cập nhật {{ timestamp(report.generatedAt) }}</span></div> -->
      <ReportOverview :report="report" @select="showSection" />
      <!-- <p class="report-mobile-hint">Vuốt ngang để xem các chỉ số →</p> -->
      <!-- <details class="report-reading-guide"><summary><Info :size="15" aria-hidden="true" /> Cách đọc báo cáo</summary><p>Chỉ tính chuyến đã khởi hành; mỗi chuyến tính một lần theo lần chạy hiện tại. Chuyến về muộn là chuyến theo lịch đã đến muộn hoặc đang quá giờ dự kiến kết thúc. Trễ trạm đếm từng lần đến trạm muộn nên một chuyến có thể có nhiều lần.</p></details> -->
      <div class="report-workspace">
      <ReportSectionTabs v-model="activeSection" />
      <p class="report-mobile-hint report-table-hint">Vuốt thanh mục để chọn báo cáo; vuốt bảng để xem thêm cột.</p>
      <section v-show="activeSection === 'occupancy'" id="report-panel-occupancy" role="tabpanel" aria-labelledby="report-tab-occupancy" tabindex="0" class="business-surface report-table-card report-occupancy-card" aria-label="Thống kê người trên xe">
        <div class="report-section-heading"><h2>Hành khách và mức sử dụng ghế</h2></div>
        <EmployeeOccupancyBreakdown :report="report" />
      </section>
      <section v-show="activeSection === 'vehicles'" id="report-panel-vehicles" role="tabpanel" aria-labelledby="report-tab-vehicles" tabindex="0" class="business-surface report-table-card" aria-label="Thống kê theo xe">
        <div class="report-section-heading"><div><h2>Thống kê theo xe <span class="report-section-count">{{ number(vehicleRows.length) }} xe</span></h2><p>Đối chiếu số chuyến, thời gian đến và số người được chở của từng xe.</p></div></div>
        <div v-if="!report.vehicles.length" class="reports-empty">Chưa có xe nào chạy chuyến trong phạm vi đã chọn.</div>
        <template v-else>
          <div class="report-table-scroll" role="region" aria-label="Bảng thống kê theo xe" tabindex="0"><ReportResourceTable :rows="pageVehicleRows" resource="vehicle" /></div>
          <PaginationControls v-model:page="vehiclePage" :page-count="pageCount(vehicleRows.length)" :total="vehicleRows.length" :page-size="reportPageSize" label="xe" />
        </template>
      </section>
      <section v-show="activeSection === 'drivers'" id="report-panel-drivers" role="tabpanel" aria-labelledby="report-tab-drivers" tabindex="0" class="business-surface report-table-card" aria-label="Thống kê theo tài xế">
        <div class="report-section-heading"><div><h2>Thống kê theo tài xế <span class="report-section-count">{{ number(driverRows.length) }} tài xế</span></h2><p>Bấm “Xem chuyến” dưới tên tài xế để xem các chuyến trong kỳ. Số lần trễ và cảnh báo không mặc nhiên là lỗi của tài xế.</p></div></div>
        <div v-if="!report.drivers.length" class="reports-empty">Chưa có tài xế nào chạy chuyến trong phạm vi đã chọn.</div>
        <template v-else>
          <div class="report-table-scroll" role="region" aria-label="Bảng thống kê theo tài xế" tabindex="0"><ReportResourceTable :rows="pageDriverRows" resource="driver" @view-driver-trips="selectedDriverReport = $event" /></div>
          <PaginationControls v-model:page="driverPage" :page-count="pageCount(driverRows.length)" :total="driverRows.length" :page-size="reportPageSize" label="tài xế" />
        </template>
      </section>
      <section v-show="activeSection === 'late-stops'" id="report-panel-late-stops" role="tabpanel" aria-labelledby="report-tab-late-stops" tabindex="0" class="business-surface report-table-card" aria-label="Các lần trễ trạm">
          <div class="report-section-heading">
            <div>
              <h2>Các lần trễ trạm <span class="report-section-count">{{ number(report.lateStops.length) }} lần</span></h2>
              <p>Giờ đến mô phỏng được so với kế hoạch ban đầu của lần chạy; chuyến không mô phỏng dùng giờ đến thực tế.</p>
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
            <div class="report-table-scroll" role="region" aria-label="Bảng chi tiết báo cáo" tabindex="0">
              <table class="report-late-stops-table">
                <thead><tr><th scope="col">Chuyến · Xe</th><th scope="col">Trạm</th><th scope="col">Dự kiến</th><th scope="col">Thực tế</th><th scope="col">Trễ</th></tr></thead>
                <tbody>
                  <tr v-for="row in pageLateStopRows" :key="`${row.tripId}-${row.stopSequence}`">
                    <td><strong>{{ row.routeName ?? 'Chưa có tên tuyến' }}</strong><small>Chuyến #{{ row.tripId }} · {{ row.vehiclePlateNumber ?? 'Chưa có xe' }}</small><small>{{ row.driverName ?? 'Chưa phân công' }}</small></td>
                    <td>{{ row.stopSequence }}. {{ row.stationName }}</td>
                    <td>{{ timestamp(row.plannedArrivalAt) }}</td>
                    <td>{{ timestamp(row.actualArrivalAt) }}</td>
                    <td class="report-danger">{{ duration(row.delaySeconds) }}</td>
                  </tr>
                </tbody>
              </table>
              </div>
              <PaginationControls v-model:page="lateStopPage" :page-count="pageCount(lateStopRows.length)" :total="lateStopRows.length" :page-size="reportPageSize" label="lần trễ trạm" />
          </div>
        </section>
        <section v-show="activeSection === 'incidents'" id="report-panel-incidents" role="tabpanel" aria-labelledby="report-tab-incidents" tabindex="0" class="business-surface report-table-card" aria-label="Sự cố và cảnh báo an toàn">
          <div class="report-section-heading"><div><h2>Sự cố và cảnh báo an toàn</h2><p>Sự cố tài xế báo, cảnh báo lệch tuyến và vượt tốc độ trong kỳ.</p></div></div>
          <div v-if="!report.incidents.length" class="reports-empty">Không ghi nhận sự cố hoặc cảnh báo an toàn.</div>
          <div v-else class="report-table-scroll" role="region" aria-label="Bảng chi tiết báo cáo" tabindex="0">
            <table class="report-incident-summary">
              <thead><tr><th scope="col">Loại</th><th scope="col">Mức độ</th><th scope="col">Số lần</th></tr></thead>
              <tbody><tr v-for="row in pageIncidentRows" :key="`${row.type}-${row.severity}`"><td>{{ incidentLabel(row.type) }}</td><td><span class="report-severity" :class="{ 'is-critical': row.severity === 'CRITICAL' }">{{ severityLabel(row.severity) }}</span></td><td>{{ number(row.count) }}</td></tr></tbody>
            </table>
            </div>
            <PaginationControls v-model:page="incidentPage" :page-count="pageCount(incidentRows.length)" :total="incidentRows.length" :page-size="reportPageSize" label="loại sự cố" />
          <template v-if="incidentDetailRows.length">
            <h3 class="report-detail-heading">Chi tiết sự cố và cảnh báo</h3>
            <div class="report-table-scroll" role="region" aria-label="Bảng chi tiết báo cáo" tabindex="0">
              <table class="report-incidents-table">
                <thead><tr><th scope="col">Chuyến · Xe · Tài xế</th><th scope="col">Loại · Nội dung</th><th scope="col">Thời điểm</th><th scope="col">Mức độ</th><th scope="col">Tình trạng</th></tr></thead>
                <tbody><tr v-for="row in pageIncidentDetails" :key="row.id">
                  <td><strong>{{ row.routeName ?? 'Chưa có tên tuyến' }}</strong><small>Chuyến #{{ row.tripId }} · {{ row.vehiclePlateNumber ?? 'Chưa có xe' }}</small><small>{{ row.driverName ?? 'Chưa phân công' }}</small></td>
                  <td><strong>{{ incidentLabel(row.type) }}</strong><small v-if="row.detail">{{ row.detail }}</small></td>
                  <td>{{ timestamp(row.occurredAt) }}</td><td><span class="report-severity" :class="{ 'is-critical': row.severity === 'CRITICAL' }">{{ severityLabel(row.severity) }}</span></td>
                  <td><span class="report-incident-status" :class="`is-${row.status.toLowerCase()}`">{{ statusLabel(row.status) }}</span></td>
                </tr></tbody>
              </table>
              </div>
              <PaginationControls v-model:page="incidentDetailPage" :page-count="pageCount(incidentDetailRows.length)" :total="incidentDetailRows.length" :page-size="reportPageSize" label="sự cố / cảnh báo" />
          </template>
        </section>
      </div>
    </template>
    <DriverReportTripsPanel v-if="selectedDriverReport && report" :driver="selectedDriverReport" :from="report.from" :to="report.to" :vehicle-label="selectedVehicle" @close="selectedDriverReport = null" />
  </div>
</template>
