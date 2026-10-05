<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import {
  AlertTriangle,
  BarChart3,
  CalendarRange,
  CheckCircle2,
  Clock3,
  MapPinned,
  RefreshCw,
  Route,
} from '@lucide/vue';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import { fetchOperationalReport } from '@/features/reports/api/reports';
import type { Driver, FleetVehicle } from '@/features/fleet/types/fleet';
import type { OperationalReport, OperationalReportFilters } from '@/features/reports/types/reports';
import PageHeading from '@/shared/components/PageHeading.vue';
import AppDatePicker from '@/shared/components/AppDatePicker.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifyError } from '@/shared/notifications/toast';
import '@/features/reports/styles/reports.css';
function dateInUtc(offset: number) {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}
function formatDistance(meters: number) {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(meters / 1000);
}
function formatRuntime(seconds: number) {
  const hours = Math.floor(seconds / 3600),
    minutes = Math.floor((seconds % 3600) / 60);
  return hours === 0 ? `${minutes} phút` : `${hours} giờ ${minutes} phút`;
}
function formatDate(value: string) {
  return value
    ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeZone: 'UTC' }).format(
        new Date(`${value}T00:00:00Z`),
      )
    : '—';
}
const filters = shallowRef<OperationalReportFilters>({ from: dateInUtc(-29), to: dateInUtc(0) });
const data = shallowRef<{
  report?: OperationalReport;
  vehicles: FleetVehicle[];
  drivers: Driver[];
} | null>(null);
const loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
useErrorToast(error);
watch(
  [filters, attempt],
  (_, _old, cleanup) => {
    if (!filters.value.from || !filters.value.to) return;
    const controller = new AbortController();
    Promise.all([
      fetchOperationalReport(filters.value, controller.signal),
      fetchFleetVehicles(controller.signal),
      fetchDrivers(controller.signal),
    ])
      .then(([report, vehicles, drivers]) => {
        if (!controller.signal.aborted) {
          data.value = { report, vehicles, drivers };
          error.value = null;
        }
      })
      .catch((reason) => {
        if (!controller.signal.aborted)
          error.value =
            reason instanceof Error ? reason.message : 'Không thể tải báo cáo vận hành.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
function applyFilters(next: OperationalReportFilters) {
  if (!next.from || !next.to) {
    loading.value = false;
    error.value = null;
    notifyError('Hãy chọn đầy đủ ngày bắt đầu và ngày kết thúc.');
  } else {
    loading.value = true;
    error.value = null;
    if (data.value) data.value = { ...data.value, report: undefined };
  }
  filters.value = next;
}
function idFilter(key: 'vehicleId' | 'driverId', event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  applyFilters({ ...filters.value, [key]: value ? Number(value) : undefined });
}
function resetFilters() {
  applyFilters({ from: dateInUtc(-29), to: dateInUtc(0) });
}
const report = computed(() => data.value?.report);
const metrics = computed(() => {
  const r = report.value;
  return r
    ? [
        {
          label: 'Tổng số chuyến',
          value: r.tripCount.toLocaleString('vi-VN'),
          detail: `${r.completedTripCount.toLocaleString('vi-VN')} chuyến đã hoàn thành`,
          icon: Route,
          tone: 'blue',
        },
        {
          label: 'Tổng quãng đường tuyến',
          value: `${formatDistance(r.totalDistanceMeters)} km`,
          detail: 'Theo tổng chiều dài tuyến đã lập',
          icon: MapPinned,
          tone: 'cyan',
        },
        {
          label: 'Tổng thời gian chạy',
          value: formatRuntime(r.totalRunningSeconds),
          detail: 'Từ thời điểm bắt đầu đến kết thúc',
          icon: Clock3,
          tone: 'violet',
        },
        {
          label: 'Tỷ lệ đúng giờ',
          value: `${r.onTimeRatePercent.toLocaleString('vi-VN')}%`,
          detail: 'Trong các chuyến theo lịch cố định đã hoàn thành',
          icon: CheckCircle2,
          tone: 'green',
        },
        {
          label: 'Chuyến bị trễ',
          value: r.lateTripCount.toLocaleString('vi-VN'),
          detail: 'Chuyến theo lịch cố định đã trễ hoặc đang quá giờ',
          icon: AlertTriangle,
          tone: 'red',
        },
        {
          label: 'Lần lệch tuyến',
          value: r.offRouteEventCount.toLocaleString('vi-VN'),
          detail: 'Cảnh báo lệch tuyến được ghi nhận',
          icon: MapPinned,
          tone: 'amber',
        },
      ]
    : [];
});
</script>
<template>
  <div class="business-page reports-page">
    <PageHeading
      eyebrow="PHÂN TÍCH HIỆU SUẤT"
      title="Báo cáo và thống kê"
      description="Đối soát hiệu suất chuyến đi theo thời gian, phương tiện và tài xế."
    >
      <template #actions>
        <button
          type="button"
          class="business-button reports-refresh"
          :disabled="loading || !filters.from || !filters.to"
          @click="attempt++"
        >
          <RefreshCw :size="16" />Làm mới
        </button>
      </template>
    </PageHeading>
    <section
      class="business-surface reports-filter-panel"
      aria-label="Bộ lọc báo cáo"
    >
      <div class="reports-filter-heading">
        <div>
          <h2>Phạm vi báo cáo</h2>
          <p>Ngày được tính theo thời điểm khởi hành dự kiến của chuyến.</p>
        </div>
        <button
          type="button"
          class="reports-reset"
          @click="resetFilters"
        >
          Xóa bộ lọc
        </button>
      </div>
      <div class="reports-filters">
        <label
          ><span>Từ ngày</span
          ><AppDatePicker
            required
            :model-value="filters.from"
            :max="filters.to"
            placeholder="Từ ngày"
            @update:model-value="applyFilters({ ...filters, from: $event })"
            @change="applyFilters({ ...filters, from: $event })"
        /></label>
        <label
          ><span>Đến ngày</span
          ><AppDatePicker
            required
            :model-value="filters.to"
            :min="filters.from"
            placeholder="Đến ngày"
            @update:model-value="applyFilters({ ...filters, to: $event })"
            @change="applyFilters({ ...filters, to: $event })"
        /></label>
        <label
          ><span>Phương tiện</span
          ><select
            :value="filters.vehicleId ?? ''"
            @change="idFilter('vehicleId', $event)"
          >
            <option value="">Tất cả phương tiện</option>
            <option
              v-for="vehicle in data?.vehicles ?? []"
              :key="vehicle.id"
              :value="vehicle.id"
            >
              {{ vehicle.plateNumber }} · {{ vehicle.name
              }}{{ vehicle.active ? '' : ' · Ngừng sử dụng' }}
            </option>
          </select></label
        >
        <label
          ><span>Tài xế</span
          ><select
            :value="filters.driverId ?? ''"
            @change="idFilter('driverId', $event)"
          >
            <option value="">Tất cả tài xế</option>
            <option
              v-for="driver in data?.drivers ?? []"
              :key="driver.id"
              :value="driver.id"
            >
              {{ driver.fullName }} · {{ driver.licenseNumber
              }}{{ driver.active ? '' : ' · Ngừng hoạt động' }}
            </option>
          </select></label
        >
      </div>
      <div class="reports-filter-note">
        <CalendarRange :size="15" /><span
          >{{ formatDate(filters.from) }} – {{ formatDate(filters.to) }}</span
        ><span class="reports-filter-separator">·</span
        ><span
          >{{ filters.vehicleId ? 'Đã lọc theo xe' : 'Toàn bộ xe' }} ·
          {{ filters.driverId ? 'Đã lọc theo tài xế' : 'Toàn bộ tài xế' }}</span
        >
      </div>
    </section>
    <section
      class="reports-metrics"
      aria-label="Chỉ số báo cáo"
      :aria-busy="loading"
    >
      <article
        v-for="metric in metrics"
        :key="metric.label"
        :class="`reports-metric ${metric.tone}`"
      >
        <span class="reports-metric-icon"
          ><component
            :is="metric.icon"
            :size="19"
        /></span>
        <div>
          <span>{{ metric.label }}</span
          ><strong>{{ loading || !report ? '—' : metric.value }}</strong
          ><small>{{ metric.detail }}</small>
        </div>
      </article>
      <div
        v-if="loading && !report"
        class="reports-loading"
        role="status"
      >
        <RefreshCw :size="18" /><span>Đang tải báo cáo vận hành…</span>
      </div>
      <div
        v-if="!loading && report?.tripCount === 0 && !error"
        class="reports-empty"
      >
        <BarChart3 :size="30" /><strong>Không có chuyến trong phạm vi này</strong>
        <p>Thử chọn khoảng thời gian khác hoặc bỏ bớt bộ lọc.</p>
      </div>
    </section>
    <!-- <section class="business-surface reports-definition">
      <div>
        <div class="reports-definition-icon"><BusFront :size="18" /></div>
        <div>
          <h2>Định nghĩa chỉ số</h2>
          <p>
            Quãng đường dùng chiều dài tuyến đã lập; thời gian chạy dùng timestamp thực tế. Quá tốc
            độ chỉ tính dữ liệu GPS và gộp các mẫu liên tiếp thành một lần vi phạm.
          </p>
        </div>
      </div>
      <div>
        <UserRound :size="16" /><span>Lọc tài xế dựa trên tài xế đang gán cho chuyến.</span>
      </div>
    </section> -->
  </div>
</template>
