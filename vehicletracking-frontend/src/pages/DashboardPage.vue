<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue';
import {
  AlertTriangle,
  ArrowRight,
  BellRing,
  BusFront,
  CalendarClock,
  CheckCircle2,
  CircleDashed,
  Clock3,
  MapPinned,
  UserRound,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import { fetchTrips } from '@/features/fleet/api/fleet';
import { fetchDashboardSummary } from '@/features/reports/api/dashboard';
import { TRIP_STATUS_LABELS, type TripSummary } from '@/features/fleet/types/fleet';
import type { DashboardSummary } from '@/features/reports/types/dashboard';
import {
  displayTripTime,
  tripDispatchLabel,
  tripReferenceTime,
} from '@/features/fleet/utils/tripTime';
import PageHeading from '@/shared/components/PageHeading.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import '@/features/reports/styles/business-pages.css';
const data = shallowRef<{ summary: DashboardSummary; trips: TripSummary[] } | null>(null);
const loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
useErrorToast(error);
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    const load = (showLoading: boolean) => {
      if (showLoading) loading.value = true;
      Promise.all([fetchDashboardSummary(controller.signal), fetchTrips(controller.signal)])
        .then(([summary, trips]) => {
          if (!controller.signal.aborted) {
            data.value = { summary, trips };
            error.value = null;
          }
        })
        .catch((reason) => {
          if (!controller.signal.aborted)
            error.value =
              reason instanceof Error ? reason.message : 'Không thể tải dữ liệu tổng quan.';
        })
        .finally(() => {
          if (!controller.signal.aborted) loading.value = false;
        });
    };
    load(true);
    const timer = window.setInterval(() => load(false), 15_000);
    cleanup(() => {
      controller.abort();
      window.clearInterval(timer);
    });
  },
  { immediate: true },
);
const recentTrips = computed(() =>
  [...(data.value?.trips ?? [])]
    .sort(
      (a, b) => Date.parse(tripReferenceTime(b)) - Date.parse(tripReferenceTime(a)) || b.id - a.id,
    )
    .slice(0, 6),
);
function isOverdue(trip: TripSummary) {
  return (
    trip.dispatchMode === 'FIXED_SCHEDULE' &&
    trip.status === 'IN_PROGRESS' &&
    Date.parse(trip.plannedEndAt) <
      Date.parse(data.value?.summary.serverTime ?? new Date().toISOString())
  );
}
const metricCards = computed(() => [
  {
    label: 'Phương tiện hoạt động',
    value: data.value?.summary.activeVehicleCount,
    detail: 'Danh mục xe đang sử dụng',
    icon: BusFront,
    tone: 'blue',
  },
  {
    label: 'Tài xế hoạt động',
    value: data.value?.summary.activeDriverCount,
    detail: 'Sẵn sàng nhận phân công',
    icon: UserRound,
    tone: 'cyan',
  },
  {
    label: 'Chuyến đang chạy',
    value: data.value?.summary.tripsInProgress,
    detail: 'Đang thực hiện trên tuyến',
    icon: Clock3,
    tone: 'amber',
  },
  {
    label: 'Chuyến chờ khởi hành',
    value: data.value?.summary.scheduledTrips,
    detail: 'Đã lập lịch và chưa bắt đầu',
    icon: CalendarClock,
    tone: 'violet',
  },
  {
    label: 'Chuyến hoàn thành',
    value: data.value?.summary.completedTrips,
    detail: 'Tổng chuyến đã hoàn thành',
    icon: CheckCircle2,
    tone: 'green',
  },
  {
    label: 'Chuyến đang trễ',
    value: data.value?.summary.overdueTrips,
    detail: 'Lịch cố định đang chạy quá giờ kế hoạch',
    icon: Clock3,
    tone: 'red',
  },
  {
    label: 'Xe lệch tuyến',
    value: data.value?.summary.offRouteVehicleCount,
    detail: 'Cảnh báo lệch tuyến đang mở',
    icon: MapPinned,
    tone: 'red',
  },
  {
    label: 'Cảnh báo cần xử lý',
    value: data.value?.summary.unreadAlertCount,
    detail: 'Thông báo chưa được đánh dấu',
    icon: BellRing,
    tone: 'amber',
  },
  {
    label: 'Chuyến đã hủy',
    value: data.value?.summary.cancelledTrips,
    detail: 'Tổng chuyến đã hủy',
    icon: AlertTriangle,
    tone: 'violet',
  },
]);
</script>
<template>
  <div class="business-page dashboard-page">
    <PageHeading
      eyebrow="TRUNG TÂM ĐIỀU HÀNH"
      title="Tổng quan vận hành"
      description="Nắm bắt tình hình đội xe, chuyến đi và những việc cần xử lý."
      ><template #actions
        ><RouterLink
          class="business-button primary"
          to="/operations"
          ><MapPinned :size="17" />Mở bản đồ giám sát
          <ArrowRight :size="15" /></RouterLink></template
    ></PageHeading>
    <div class="dashboard-section-label">
      <h3>Hoạt động đội xe</h3>
      <!-- <span>Cập nhật mỗi 15 giây</span> -->
    </div>
    <section
      class="dashboard-metrics"
      :aria-busy="loading"
      aria-label="Chỉ số vận hành"
    >
      <article
        v-for="card in metricCards"
        :key="card.label"
        :class="`dashboard-metric ${card.tone}`"
      >
        <span class="dashboard-metric-icon"
          ><component
            :is="card.icon"
            :size="20"
        /></span>
        <div>
          <span>{{ card.label }}</span
          ><strong
            ><span
              v-if="loading || card.value === undefined"
              class="dashboard-loading-value"
            /><template v-else>{{ card.value }}</template></strong
          ><small>{{ card.detail }}</small>
        </div>
      </article>
    </section>
    <div class="dashboard-columns">
      <section class="business-surface dashboard-trips">
        <div class="dashboard-card-heading">
          <div>
            <h2>Chuyến đi gần đây</h2>
            <p>Danh sách theo thời gian khởi hành đã lập.</p>
          </div>
          <RouterLink to="/trips">Xem tất cả <ArrowRight :size="14" /></RouterLink>
        </div>
        <p
          v-if="loading"
          class="dashboard-state"
          role="status"
        >
          Đang tải chuyến đi…
        </p>
        <div
          v-if="!loading && !error && recentTrips.length === 0"
          class="dashboard-empty"
        >
          <CalendarClock :size="28" /><strong>Chưa có chuyến đi</strong>
          <p>Tạo chuyến đầu tiên từ module Chuyến đi.</p>
          <RouterLink to="/trips">Tạo chuyến</RouterLink>
        </div>
        <div
          v-if="!loading && recentTrips.length > 0"
          class="dashboard-trip-list"
        >
          <RouterLink
            v-for="trip in recentTrips"
            :key="trip.id"
            to="/trips"
            class="dashboard-trip-row"
            ><span class="dashboard-trip-id">#{{ trip.id }}</span
            ><span
              ><strong>{{ trip.routeName }}</strong
              ><small
                >{{ trip.vehiclePlateNumber }} ·
                {{ trip.driver?.fullName ?? 'Chưa gán tài xế' }}</small
              ></span
            ><time :title="tripDispatchLabel(trip)">{{
              displayTripTime(tripReferenceTime(trip))
            }}</time
            ><span :class="`dashboard-status ${trip.status.toLowerCase()}`">{{
              isOverdue(trip) ? 'Đang trễ' : TRIP_STATUS_LABELS[trip.status]
            }}</span></RouterLink
          >
        </div>
      </section>
      <aside class="dashboard-side-column">
        <section class="business-surface dashboard-quick-links">
          <div class="dashboard-card-heading">
            <div>
              <h2>Thao tác nhanh</h2>
              <p>Đi đến nghiệp vụ thường dùng.</p>
            </div>
          </div>
          <RouterLink to="/vehicles"
            ><BusFront :size="18" /><span
              ><strong>Quản lý phương tiện</strong><small>Hồ sơ xe và phân công tài xế</small></span
            ><ArrowRight :size="15"
          /></RouterLink>
          <RouterLink to="/drivers"
            ><UserRound :size="18" /><span
              ><strong>Quản lý tài xế</strong><small>Thông tin và trạng thái hoạt động</small></span
            ><ArrowRight :size="15"
          /></RouterLink>
          <RouterLink to="/trips"
            ><CalendarClock :size="18" /><span
              ><strong>Lập chuyến đi</strong><small>Chọn xe, tuyến và tài xế</small></span
            ><ArrowRight :size="15"
          /></RouterLink>
        </section>
        <section class="business-surface dashboard-alerts">
          <div class="dashboard-card-heading">
            <div>
              <h2>Cảnh báo cần xử lý</h2>
              <p>
                {{
                  data
                    ? `${data.summary.unreadAlertCount} cảnh báo chưa đọc`
                    : 'Đang tải dữ liệu cảnh báo'
                }}
              </p>
            </div>
            <RouterLink to="/alerts">Mở trung tâm <ArrowRight :size="14" /></RouterLink>
          </div>
          <p
            v-if="loading"
            class="dashboard-state"
            role="status"
          >
            Đang tải cảnh báo…
          </p>
          <div
            v-if="!loading && !error && data?.summary.pendingAlerts.length === 0"
            class="dashboard-empty"
          >
            <CircleDashed :size="28" /><strong>Không có cảnh báo mới</strong>
            <p>Hệ thống sẽ hiển thị cảnh báo khi có sự cố cần xử lý.</p>
          </div>
          <template v-if="!loading"
            ><RouterLink
              v-for="alert in data?.summary.pendingAlerts"
              :key="alert.id"
              :to="`/operations?tripId=${alert.tripId}`"
              class="dashboard-alert-row"
              ><span
                :class="`dashboard-alert-icon ${alert.type === 'OFF_ROUTE_DETECTED' ? 'off-route' : 'reroute'}`"
                ><MapPinned
                  v-if="alert.type === 'OFF_ROUTE_DETECTED'"
                  :size="15" /><AlertTriangle
                  v-else
                  :size="15" /></span
              ><span
                ><strong>{{ alert.title }}</strong
                ><small>{{ alert.vehiclePlateNumber }} · Chuyến #{{ alert.tripId }}</small></span
              ><ArrowRight :size="14" /></RouterLink
          ></template>
        </section>
      </aside>
    </div>
  </div>
</template>
