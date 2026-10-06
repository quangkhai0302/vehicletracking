<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import {
  CalendarClock,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Navigation,
  RefreshCw,
  Route,
  UserRound,
} from '@lucide/vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { useAuth } from '@/features/auth/composables/useAuth';
import { fetchMySchedules, fetchMyTrip, fetchMyTrips } from '@/features/fleet/api/driverPortal';
import {
  TRIP_STATUS_LABELS,
  type TripDetail,
  type TripSummary,
} from '@/features/fleet/types/fleet';
import { tripDispatchLabel, tripReferenceTime } from '@/features/fleet/utils/tripTime';
import type { TripSchedule } from '@/features/schedules/types/schedule';
import DriverTripDetail from '@/features/fleet/components/DriverTripDetail.vue';
import DriverDispatchWorkspace from '@/features/dispatch/components/DriverDispatchWorkspace.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
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
const loadedOnce = ref(false);
useErrorToast(error);
const schedulesView = computed(() => route.path.endsWith('/schedules'));
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    loading.value = !loadedOnce.value;
    error.value = null;
    Promise.all([fetchMyTrips({}, controller.signal), fetchMySchedules(controller.signal)])
      .then(([t, s]) => {
        if (!controller.signal.aborted) {
          trips.value = t;
          schedules.value = s;
          loadedOnce.value = true;
          if (detail.value && !t.some((trip) => trip.id === detail.value?.trip.id))
            detail.value = null;
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
    (trip) => businessDateKey(tripReferenceTime(trip)) === businessDateKey(new Date()),
  ),
);
const nextTrip = computed(
  () =>
    trips.value
      .filter((trip) => trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS')
      .sort((a, b) => Date.parse(tripReferenceTime(a)) - Date.parse(tripReferenceTime(b)))[0],
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
const refreshTimer = setInterval(() => {
  if (document.visibilityState === 'visible') attempt.value++;
}, 15000);
onScopeDispose(() => clearInterval(refreshTimer));
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
        <DriverDispatchWorkspace
          :account-name="auth.user?.driverName ?? auth.user?.username ?? 'Tài xế'"
          @changed="attempt++"
          @password-change="router.push('/driver/change-password')"
          @sign-out="signOut"
        />
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
            <span>{{ loading ? 'Đang tải…' : `${trips.length} chuyến` }}</span
            ><button
              type="button"
              class="driver-panel-refresh"
              :disabled="loading"
              aria-label="Tải lại chuyến được phân công"
              @click="attempt++"
            >
              <RefreshCw :size="14" />
            </button>
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
                  >{{ trip.vehiclePlateNumber }} · {{ tripDispatchLabel(trip) }} ·
                  {{ dateTime(tripReferenceTime(trip)) }}</small
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
          <span>{{ loading ? 'Đang tải…' : `${schedules.length} lịch` }}</span
          ><button
            type="button"
            class="driver-panel-refresh"
            :disabled="loading"
            aria-label="Tải lại lịch chạy"
            @click="attempt++"
          >
            <RefreshCw :size="14" />
          </button>
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
    <DriverTripDetail
      v-if="detail"
      :detail="detail"
      :format-time="dateTime"
      @close="detail = null"
    />
  </main>
</template>
