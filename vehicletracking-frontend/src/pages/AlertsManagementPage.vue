<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import {
  AlertTriangle,
  BellRing,
  Check,
  CheckCheck,
  MapPinned,
  RefreshCw,
  Route,
  Trash2,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import {
  deleteNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/features/reports/api/notifications';
import type { NotificationItem } from '@/features/reports/types/notifications';
import PageHeading from '@/shared/components/PageHeading.vue';
import '@/features/reports/styles/alerts-management.css';
type TypeFilter = 'ALL' | 'OFF_ROUTE_DETECTED' | 'REROUTE';
type SeverityFilter = 'ALL' | 'CRITICAL' | 'MAJOR';
const formatDistance = (meters?: number | null) =>
  meters == null ? null : `${Math.round(meters)} m`;
const formatDateTime = (value: string) =>
  new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(
    new Date(value),
  );
function alertDetail(item: NotificationItem) {
  if (item.type !== 'OFF_ROUTE_DETECTED') return item.reason;
  const distance = formatDistance(item.measuredDistanceMeters),
    threshold = formatDistance(item.thresholdDistanceMeters);
  const duration = item.breachDurationSeconds == null ? null : `${item.breachDurationSeconds}s`;
  return (
    [
      distance && `Khoảng cách ${distance}`,
      threshold && `ngưỡng ${threshold}`,
      duration && `duy trì ${duration}`,
    ]
      .filter(Boolean)
      .join(' · ') || item.reason
  );
}
const items = shallowRef<NotificationItem[]>([]),
  loading = ref(true),
  error = ref<string | null>(null),
  attempt = ref(0);
const typeFilter = ref<TypeFilter>('ALL'),
  severityFilter = ref<SeverityFilter>('ALL');
const busyId = ref<number | null>(null),
  confirmDelete = shallowRef<NotificationItem | null>(null),
  deleteError = ref<string | null>(null);
let disposed = false;
onScopeDispose(() => {
  disposed = true;
});
watch(
  attempt,
  (_, _old, cleanup) => {
    const controller = new AbortController();
    function load(showLoading: boolean) {
      if (showLoading) loading.value = true;
      fetchNotifications(false, controller.signal)
        .then((next) => {
          if (!controller.signal.aborted) {
            items.value = next;
            error.value = null;
          }
        })
        .catch((reason) => {
          if (!controller.signal.aborted)
            error.value = reason instanceof Error ? reason.message : 'Không thể tải cảnh báo.';
        })
        .finally(() => {
          if (!controller.signal.aborted) loading.value = false;
        });
    }
    load(true);
    const timer = window.setInterval(() => load(false), 15_000);
    cleanup(() => {
      controller.abort();
      window.clearInterval(timer);
    });
  },
  { immediate: true },
);
const filtered = computed(() =>
  items.value.filter((item) => {
    const typeMatch =
      typeFilter.value === 'ALL' ||
      (typeFilter.value === 'OFF_ROUTE_DETECTED'
        ? item.type === typeFilter.value
        : item.type === 'REROUTE_CREATED' || item.type === 'REROUTE_UNAVAILABLE');
    return typeMatch && (severityFilter.value === 'ALL' || item.severity === severityFilter.value);
  }),
);
const unread = computed(() => items.value.filter((item) => !item.readAt).length);
const offRouteCount = computed(
  () => items.value.filter((item) => item.type === 'OFF_ROUTE_DETECTED').length,
);
async function read(item: NotificationItem) {
  if (busyId.value !== null || item.readAt) return;
  busyId.value = item.id;
  error.value = null;
  try {
    const updated = await markNotificationRead(item.id);
    if (!disposed) items.value = items.value.map((row) => (row.id === updated.id ? updated : row));
  } catch (reason) {
    if (!disposed)
      error.value = reason instanceof Error ? reason.message : 'Không thể cập nhật cảnh báo.';
  } finally {
    if (!disposed) busyId.value = null;
  }
}
async function readAll() {
  if (busyId.value !== null || unread.value === 0) return;
  busyId.value = -1;
  error.value = null;
  try {
    await markAllNotificationsRead();
    if (!disposed) {
      const now = new Date().toISOString();
      items.value = items.value.map((item) => (item.readAt ? item : { ...item, readAt: now }));
    }
  } catch (reason) {
    if (!disposed)
      error.value = reason instanceof Error ? reason.message : 'Không thể đánh dấu cảnh báo.';
  } finally {
    if (!disposed) busyId.value = null;
  }
}
async function remove() {
  if (!confirmDelete.value || busyId.value !== null) return;
  const id = confirmDelete.value.id;
  busyId.value = id;
  deleteError.value = null;
  try {
    await deleteNotification(id);
    if (!disposed) {
      items.value = items.value.filter((item) => item.id !== id);
      confirmDelete.value = null;
    }
  } catch (reason) {
    if (!disposed)
      deleteError.value = reason instanceof Error ? reason.message : 'Không thể xóa cảnh báo.';
  } finally {
    if (!disposed) busyId.value = null;
  }
}
</script>
<template>
  <div class="business-page alerts-management-page">
    <PageHeading
      eyebrow="GIÁM SÁT & XỬ LÝ"
      title="Cảnh báo vận hành"
      description="Theo dõi sự cố, xác nhận xử lý và kiểm tra hành trình liên quan."
      ><template #actions
        ><button
          class="business-button"
          :disabled="loading"
          @click="attempt++"
        >
          <RefreshCw :size="16" />Làm mới
        </button></template
      ></PageHeading
    >
    <section
      class="alerts-metrics"
      aria-label="Chỉ số cảnh báo"
      :aria-busy="loading"
    >
      <article>
        <BellRing :size="19" />
        <div>
          <span>Đang hiển thị</span><strong>{{ loading ? '—' : items.length }}</strong
          ><small>Tối đa 50 cảnh báo gần nhất</small>
        </div>
      </article>
      <article>
        <AlertTriangle :size="19" />
        <div>
          <span>Chưa đọc</span><strong>{{ loading ? '—' : unread }}</strong>
        </div>
      </article>
      <article>
        <MapPinned :size="19" />
        <div>
          <span>Lệch tuyến</span><strong>{{ loading ? '—' : offRouteCount }}</strong>
        </div>
      </article>
    </section>
    <section
      v-if="error"
      class="alerts-error"
      role="alert"
    >
      <AlertTriangle :size="18" />
      <div>
        <strong>Không thể tải cảnh báo</strong>
        <p>{{ error }}</p>
      </div>
      <button @click="attempt++">Thử lại</button>
    </section>
    <section class="business-surface alerts-list-panel">
      <div class="alerts-list-heading">
        <div>
          <h2>Danh sách cảnh báo</h2>
          <p>Cập nhật tự động mỗi 15 giây.</p>
        </div>
        <button
          class="alerts-read-all"
          :disabled="busyId !== null || unread === 0"
          @click="readAll"
        >
          <CheckCheck :size="14" />Đọc tất cả
        </button>
      </div>
      <div class="alerts-filters">
        <label
          >Loại<select v-model="typeFilter">
            <option value="ALL">Tất cả</option>
            <option value="OFF_ROUTE_DETECTED">Lệch tuyến</option>
            <option value="REROUTE">Đổi tuyến</option>
          </select></label
        ><label
          >Mức độ<select v-model="severityFilter">
            <option value="ALL">Tất cả</option>
            <option value="CRITICAL">Nghiêm trọng</option>
            <option value="MAJOR">Cao</option>
          </select></label
        ><span>{{ filtered.length }} cảnh báo phù hợp</span>
      </div>
      <p
        v-if="loading"
        class="alerts-state"
        role="status"
      >
        Đang tải cảnh báo…
      </p>
      <div
        v-if="!loading && !error && filtered.length === 0"
        class="alerts-empty-state"
      >
        <BellRing :size="30" /><strong>{{
          items.length ? 'Không có cảnh báo phù hợp' : 'Chưa có cảnh báo'
        }}</strong>
        <p>
          {{
            items.length
              ? 'Thử thay đổi bộ lọc để xem các cảnh báo khác.'
              : 'Hệ thống sẽ hiển thị cảnh báo khi phát hiện xe lệch khỏi tuyến được giao.'
          }}
        </p>
      </div>
      <div
        v-if="!loading && filtered.length > 0"
        class="alerts-management-list"
      >
        <article
          v-for="item in filtered"
          :key="item.id"
          :class="`alerts-management-card ${item.readAt ? 'read' : 'unread'}`"
        >
          <div
            :class="`alerts-management-icon ${item.type === 'OFF_ROUTE_DETECTED' ? 'off-route' : 'reroute'}`"
          >
            <MapPinned
              v-if="item.type === 'OFF_ROUTE_DETECTED'"
              :size="18"
            /><Route
              v-else
              :size="18"
            />
          </div>
          <div class="alerts-management-body">
            <div class="alerts-management-top">
              <span :class="`alerts-severity ${item.severity.toLowerCase()}`">{{
                item.severity === 'CRITICAL' ? 'Nghiêm trọng' : 'Cao'
              }}</span
              ><time>{{ formatDateTime(item.createdAt) }}</time>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.vehiclePlateNumber }} · Chuyến #{{ item.tripId }}</p>
            <strong>{{ alertDetail(item) }}</strong>
            <div class="alerts-management-actions">
              <RouterLink :to="`/operations?tripId=${item.tripId}`">Mở giám sát</RouterLink
              ><button
                v-if="!item.readAt"
                :disabled="busyId !== null"
                @click="read(item)"
              >
                <Check :size="14" />Đã xử lý</button
              ><button
                :disabled="busyId !== null"
                @click="
                  deleteError = null;
                  confirmDelete = item;
                "
              >
                <Trash2 :size="14" />Xóa
              </button>
            </div>
          </div>
        </article>
      </div>
    </section>
    <FleetConfirmDialog
      v-if="confirmDelete"
      title="Xóa cảnh báo?"
      message="Cảnh báo sẽ bị xóa khỏi danh sách. Dữ liệu telemetry và trip không bị ảnh hưởng."
      confirm-label="Xác nhận xóa"
      :busy="busyId !== null"
      :error="deleteError"
      :on-close="() => (confirmDelete = null)"
      :on-confirm="remove"
    />
  </div>
</template>
