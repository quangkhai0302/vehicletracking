<script setup lang="ts">
import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue';
import { BellOff, BellRing, Route, Check, Trash2, CheckCheck, MapPinned } from '@lucide/vue';
import type { NotificationItem } from '@/features/reports/types/notifications';
import {
  deleteNotification,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/features/reports/api/notifications';
import FleetConfirmDialog from '@/features/fleet/components/FleetConfirmDialog.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifySuccess } from '@/shared/notifications/toast';
const props = defineProps<{
  notifications: NotificationItem[];
  onUnreadCountChange?: (count: number) => void;
}>();
const readIds = ref(new Set<number>()),
  deletedIds = ref(new Set<number>());
const busy = ref(false),
  error = ref<string | null>(null),
  confirmDelete = shallowRef<NotificationItem | null>(null);
useErrorToast(error);
let alive = true;
onScopeDispose(() => {
  alive = false;
});
const items = computed(() =>
  props.notifications
    .filter((item) => !deletedIds.value.has(item.id))
    .map((item) =>
      readIds.value.has(item.id) && !item.readAt
        ? { ...item, readAt: new Date().toISOString() }
        : item,
    ),
);
const unread = computed(() => items.value.filter((item) => !item.readAt).length);
watch([unread, () => props.onUnreadCountChange], ([count, callback]) => callback?.(count), {
  immediate: true,
});
const read = async (id: number) => {
  if (busy.value) return;
  busy.value = true;
  error.value = null;
  try {
    const updated = await markNotificationRead(id);
    if (alive) {
      readIds.value.add(updated.id);
      notifySuccess('Đã đánh dấu thông báo là đã đọc.');
    }
  } catch (err) {
    if (alive) error.value = err instanceof Error ? err.message : 'Không thể cập nhật thông báo.';
  } finally {
    if (alive) busy.value = false;
  }
};
const readAll = async () => {
  if (busy.value || unread.value === 0) return;
  const ids = items.value.map((item) => item.id);
  busy.value = true;
  error.value = null;
  try {
    await markAllNotificationsRead();
    if (alive) {
      ids.forEach((id) => readIds.value.add(id));
      notifySuccess('Đã đánh dấu tất cả thông báo là đã đọc.');
    }
  } catch (err) {
    if (alive) error.value = err instanceof Error ? err.message : 'Không thể đánh dấu thông báo.';
  } finally {
    if (alive) busy.value = false;
  }
};
const remove = async () => {
  const candidate = confirmDelete.value;
  if (!candidate || busy.value) return;
  busy.value = true;
  error.value = null;
  try {
    await deleteNotification(candidate.id);
    if (alive) {
      deletedIds.value.add(candidate.id);
      confirmDelete.value = null;
      notifySuccess('Đã xóa thông báo.');
    }
  } catch (err) {
    if (alive) error.value = err instanceof Error ? err.message : 'Không thể xóa thông báo.';
  } finally {
    if (alive) busy.value = false;
  }
};
</script>
<template>
  <section
    class="alert-stream-content"
    aria-label="Luồng cảnh báo"
  >
    <div class="source-label">
      <span class="status-dot" /> {{ unread }} chưa đọc
      <span class="alert-actions"
        ><button
          class="fleet-text-button"
          :disabled="busy || unread === 0"
          @click="readAll"
        >
          <CheckCheck :size="13" />Đọc tất cả
        </button></span
      >
    </div>
    <div
      v-if="items.length === 0"
      class="alerts-empty"
    >
      <BellOff :size="24" /><strong>Chưa có thông báo vận hành</strong>
      <p>Cảnh báo lệch tuyến và thông báo đổi tuyến sẽ xuất hiện tại đây khi có sự kiện.</p>
    </div>
    <div
      v-else
      class="alert-list"
    >
      <article
        v-for="item in items.slice(0, 20)"
        :key="item.id"
        :class="`alert-item ${item.readAt ? 'read' : 'unread'}`"
      >
        <div class="alert-item-icon">
          <MapPinned
            v-if="item.type === 'OFF_ROUTE_DETECTED'"
            :size="15"
          /><Route
            v-else-if="item.type === 'REROUTE_CREATED' || item.type === 'DRIVER_ROUTE_CHANGED'"
            :size="15"
          /><BellRing
            v-else-if="item.type.startsWith('DISPATCH_') || item.type === 'DRIVER_UNAVAILABLE' || item.type === 'TRIP_AUTO_STARTED'"
            :size="15"
          /><BellOff
            v-else
            :size="15"
          />
        </div>
        <div class="alert-item-body">
          <strong>{{ item.title }}</strong
          ><span
            >Chuyến #{{ item.tripId }} · {{ item.vehiclePlateNumber }} ·
            {{ item.severity === 'CRITICAL' ? 'Nghiêm trọng' : 'Cao' }}</span
          >
          <p>{{ item.reason }}</p>
        </div>
        <button
          v-if="!item.readAt"
          class="alert-read-button"
          :disabled="busy"
          aria-label="Đánh dấu đã đọc"
          @click="read(item.id)"
        >
          <Check :size="14" />
        </button>
        <button
          class="alert-read-button"
          :disabled="busy"
          aria-label="Xóa thông báo"
          @click="confirmDelete = item"
        >
          <Trash2 :size="14" />
        </button>
      </article>
    </div>
    <div class="alert-legend">
      <span><MapPinned :size="13" /> Lệch tuyến</span
      ><span><Route :size="13" /> Đổi tuyến tự động</span>
    </div>
    <FleetConfirmDialog
      v-if="confirmDelete"
      title="Xóa thông báo?"
      message="Thông báo sẽ bị xóa khỏi danh sách. Dữ liệu chuyến và revision không bị ảnh hưởng."
      confirm-label="Xác nhận xóa"
      :busy="busy"
      :on-close="
        () => {
          confirmDelete = null;
        }
      "
      :on-confirm="
        () => {
          void remove();
        }
      "
    />
  </section>
</template>
