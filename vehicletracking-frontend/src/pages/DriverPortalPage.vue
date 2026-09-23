<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import {
  CalendarClock,
  CheckCircle2,
  ChevronRight,
  Clock3,
  LogOut,
  MapPin,
  Navigation,
  Route,
  UserRound,
  X,
} from '@lucide/vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { useAuth } from '@/features/auth/composables/useAuth';
import { fetchMySchedules, fetchMyTrip, fetchMyTrips } from '@/features/fleet/api/driverPortal';
import { TRIP_STATUS_LABELS, type TripDetail, type TripSummary } from '@/features/fleet/types/fleet';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import SidePanel from '@/shared/components/SidePanel.vue';
import '@/features/fleet/styles/driver-portal.css';
const BUSINESS_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const dateTime = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: BUSINESS_TIME_ZONE,
      }).format(new Date(value))
    : 'Chưa có';
function businessDateKey(value: Date | string) {
  const values = Object.fromEntries(
    new Intl.DateTimeFormat('en', {
      timeZone: BUSINESS_TIME_ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
      .formatToParts(new Date(value))
      .map((part) => [part.type, part.value]),
  );
  return `${values.year}-${values.month}-${values.day}`;
}
const auth = useAuth(),
  route = useRoute(),
  router = useRouter();
const trips = shallowRef<TripSummary[]>([]),
  schedules = shallowRef<TripSchedule[]>([]),
  detail = shallowRef<TripDetail | null>(null);
const loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
const schedulesView = computed(() => route.path.endsWith('/schedules'));
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    loading.value = true;
    error.value = null;
    Promise.all([fetchMyTrips({}, controller.signal), fetchMySchedules(controller.signal)])
      .then(([t, s]) => {
        if (!controller.signal.aborted) {
          trips.value = t;
          schedules.value = s;
        }
      })
      .catch((reason) => {
        if (!controller.signal.aborted)
          error.value =
            reason instanceof Error ? reason.message : 'Không thể tải dữ liệu được phân công.';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading.value = false;
      });
    cleanup(() => controller.abort());
  },
  { immediate: true },
);
const todayTrips = computed(() =>
  trips.value.filter(
    (trip) => businessDateKey(trip.scheduledDepartureAt) === businessDateKey(new Date()),
  ),
);
const nextTrip = computed(
  () =>
    trips.value
      .filter((trip) => trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS')
      .sort((a, b) => Date.parse(a.scheduledDepartureAt) - Date.parse(b.scheduledDepartureAt))[0],
);
let detailRequest: AbortController | null = null;
async function openTrip(trip: TripSummary) {
  detailRequest?.abort();
  const request = new AbortController();
  detailRequest = request;
  try {
    const next = await fetchMyTrip(trip.id, request.signal);
    if (!request.signal.aborted) detail.value = next;
  } catch (reason) {
    if (!request.signal.aborted)
      error.value = reason instanceof Error ? reason.message : 'Không thể tải chi tiết chuyến.';
  }
}
onScopeDispose(() => detailRequest?.abort());
async function signOut() {
  await auth.logout();
  await router.replace('/login');
}
</script>
<template>
  <main class="driver-portal business-ui">
    <header class="driver-portal-header">
      <div class="driver-portal-brand">
        <span><Navigation :size="19" /></span>
        <div><strong>Vehicle Tracking</strong><small>Cổng tài xế</small></div>
      </div>
      <div class="driver-portal-account">
        <UserRound :size="16" /><span>{{ auth.user?.driverName ?? auth.user?.username }}</span
        ><button @click="signOut"><LogOut :size="15" />Đăng xuất</button>
      </div>
    </header>
    <div class="driver-portal-content">
      <section class="driver-welcome">
        <div>
          <span>LỊCH TRÌNH CỦA TÔI</span>
          <h1>Xin chào, {{ auth.user?.driverName ?? 'tài xế' }}</h1>
          <p>Chỉ hiển thị chuyến đi và lịch chạy được điều phối cho bạn.</p>
        </div>
        <div class="driver-welcome-icon"><UserRound :size="30" /></div>
      </section>
      <nav class="driver-tabs">
        <RouterLink
          :class="!schedulesView ? 'active' : undefined"
          to="/driver/today"
          ><Clock3 :size="16" />Chuyến của tôi</RouterLink
        ><RouterLink
          :class="schedulesView ? 'active' : undefined"
          to="/driver/schedules"
          ><CalendarClock :size="16" />Lịch chạy</RouterLink
        >
      </nav>
      <div
        v-if="error"
        class="driver-error"
        role="alert"
      >
        <span>{{ error }}</span
        ><button @click="attempt++">Thử lại</button>
      </div>
      <template v-if="!schedulesView"
        ><section class="driver-summary-grid">
          <article>
            <Clock3 :size="20" /><span
              >Chuyến hôm nay<strong>{{ loading ? '—' : todayTrips.length }}</strong></span
            >
          </article>
          <article>
            <Route :size="20" /><span
              >Chuyến kế tiếp<strong>{{
                loading ? '—' : nextTrip ? `#${nextTrip.id}` : 'Không có'
              }}</strong></span
            >
          </article>
          <article>
            <CheckCircle2 :size="20" /><span
              >Đã hoàn thành<strong>{{
                loading ? '—' : trips.filter((trip) => trip.status === 'COMPLETED').length
              }}</strong></span
            >
          </article>
        </section>
        <section class="driver-panel">
          <div class="driver-panel-heading">
            <div>
              <h2>Chuyến được phân công</h2>
              <p>Chọn một chuyến để xem lịch trình và các điểm dừng.</p>
            </div>
            <span>{{ loading ? 'Đang tải…' : `${trips.length} chuyến` }}</span>
          </div>
          <p
            v-if="loading"
            class="driver-state"
          >
            Đang tải chuyến đi…
          </p>
          <div
            v-if="!loading && trips.length === 0"
            class="driver-empty"
          >
            <Route :size="30" /><strong>Chưa có chuyến được phân công</strong>
            <p>Điều phối viên sẽ hiển thị chuyến mới tại đây.</p>
          </div>
          <div
            v-if="!loading && trips.length > 0"
            class="driver-trip-list"
          >
            <button
              v-for="trip in trips"
              :key="trip.id"
              class="driver-trip-card"
              @click="openTrip(trip)"
            >
              <span class="driver-trip-id">#{{ trip.id }}</span
              ><span
                ><strong>{{ trip.routeName }}</strong
                ><small
                  >{{ trip.vehiclePlateNumber }} · {{ dateTime(trip.scheduledDepartureAt) }}</small
                ></span
              ><span :class="`driver-status ${trip.status.toLowerCase()}`">{{
                TRIP_STATUS_LABELS[trip.status]
              }}</span
              ><ChevronRight :size="17" />
            </button>
          </div></section
      ></template>
      <section
        v-if="schedulesView"
        class="driver-panel"
      >
        <div class="driver-panel-heading">
          <div>
            <h2>Lịch chạy được giao</h2>
            <p>Lịch cố định có tài xế là bạn.</p>
          </div>
          <span>{{ loading ? 'Đang tải…' : `${schedules.length} lịch` }}</span>
        </div>
        <p
          v-if="loading"
          class="driver-state"
        >
          Đang tải lịch chạy…
        </p>
        <div
          v-if="!loading && schedules.length === 0"
          class="driver-empty"
        >
          <CalendarClock :size="30" /><strong>Chưa có lịch chạy</strong>
          <p>Liên hệ điều phối viên nếu bạn cần cập nhật phân công.</p>
        </div>
        <template v-if="!loading"
          ><article
            v-for="schedule in schedules"
            :key="schedule.id"
            class="driver-schedule-card"
          >
            <div class="driver-schedule-icon"><CalendarClock :size="19" /></div>
            <div>
              <strong>{{ schedule.name || schedule.routeName }}</strong>
              <p>{{ schedule.routeName }} · {{ schedule.vehiclePlate }}</p>
              <small
                >{{
                  schedule.frequency === 'WEEKLY'
                    ? `Hàng tuần · ${schedule.departureTime.slice(0, 5)}`
                    : `Một lần · ${schedule.scheduledDate ?? '—'}`
                }}
                · {{ schedule.timezone }}</small
              >
            </div>
            <span :class="schedule.enabled ? 'driver-schedule-active' : 'driver-schedule-paused'">{{
              schedule.enabled ? 'Đang hoạt động' : 'Tạm dừng'
            }}</span>
          </article></template
        >
      </section>
    </div>
    <SidePanel
      v-if="detail"
      class-name="driver-detail"
      label="Chi tiết chuyến được phân công"
      :on-close="() => (detail = null)"
      ><header>
        <div>
          <span>CHI TIẾT CHUYẾN #{{ detail.trip.id }}</span>
          <h2>{{ detail.trip.routeName }}</h2>
        </div>
        <button
          aria-label="Đóng chi tiết"
          @click="detail = null"
        >
          <X :size="19" />
        </button>
      </header>
      <div class="driver-detail-meta">
        <div>
          <MapPin :size="16" /><span
            >Xe<strong>{{ detail.trip.vehiclePlateNumber }}</strong></span
          >
        </div>
        <div>
          <Clock3 :size="16" /><span
            >Khởi hành<strong>{{ dateTime(detail.trip.scheduledDepartureAt) }}</strong></span
          >
        </div>
      </div>
      <div class="driver-stop-list">
        <h3>Trình tự điểm dừng</h3>
        <div
          v-for="stop in detail.stops"
          :key="stop.sequenceNumber"
        >
          <span>{{ stop.sequenceNumber }}</span>
          <div>
            <strong>{{ stop.stationName }}</strong
            ><small>Dự kiến đến {{ dateTime(stop.plannedArrivalAt) }}</small>
          </div>
        </div>
      </div></SidePanel
    >
  </main>
</template>
