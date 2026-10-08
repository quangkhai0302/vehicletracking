import { computed, onScopeDispose, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import {
  deleteNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  acknowledgeIncident,
} from '../api/notifications';
import type { NotificationItem } from '../types/notifications';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifySuccess } from '@/shared/notifications/toast';

export function useAdminNotifications(
  liveNotifications?: MaybeRefOrGetter<NotificationItem[] | null>,
) {
  const loadedOnce = ref(false);
  const confirmedReadAt = new Map<number, string>();
  const confirmedDeletedIds = new Set<number>();
  const items = shallowRef<NotificationItem[]>([]),
    loading = ref(true),
    error = ref<string | null>(null),
    attempt = ref(0);
  const typeFilter = ref<'ALL' | 'REROUTE' | 'DISPATCH' | 'INCIDENT'>('ALL'),
    severityFilter = ref<'ALL' | 'CRITICAL' | 'MAJOR'>('ALL');
  const busyId = ref<number | null>(null),
    confirmDelete = shallowRef<NotificationItem | null>(null),
    deleteError = ref<string | null>(null);
  useErrorToast(error);
  useErrorToast(deleteError);
  let disposed = false;
  onScopeDispose(() => {
    disposed = true;
  });
  function replaceItems(next: NotificationItem[]) {
    items.value = next
      .slice(0, 50)
      .filter((item) => !confirmedDeletedIds.has(item.id))
      .map((item) => {
        const readAt = confirmedReadAt.get(item.id);
        return readAt && !item.readAt ? { ...item, readAt } : item;
      });
  }
  if (liveNotifications) {
    watch(
      () => toValue(liveNotifications),
      (next) => {
        if (!next) return;
        replaceItems(next);
        error.value = null;
        loading.value = false;
        loadedOnce.value = true;
      },
      { immediate: true },
    );
  }
  watch(
    attempt,
    (_, _old, cleanup) => {
      let activeRequest: AbortController | null = null;
      function load(showLoading: boolean) {
        activeRequest?.abort();
        const controller = new AbortController();
        activeRequest = controller;
        if (showLoading) loading.value = true;
        fetchNotifications(false, controller.signal)
          .then((next) => {
            if (!controller.signal.aborted) {
              replaceItems(next);
              error.value = null;
              loadedOnce.value = true;
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
      load(!loadedOnce.value);
      const timer = window.setInterval(() => {
        if (document.visibilityState === 'visible') load(false);
      }, 15_000);
      cleanup(() => {
        activeRequest?.abort();
        window.clearInterval(timer);
      });
    },
    { immediate: true },
  );
  const filtered = computed(() =>
    items.value.filter((item) => {
      const typeMatch =
        typeFilter.value === 'ALL' ||
        (typeFilter.value === 'DISPATCH'
          ? item.type === 'DISPATCH_ATTENTION' ||
            item.type === 'DRIVER_UNAVAILABLE' ||
            item.type === 'DISPATCH_REASSIGNED' ||
            item.type === 'TRIP_AUTO_STARTED' ||
            item.type === 'DIRECT_ASSIGNMENT_ACCEPTED' ||
            item.type === 'DIRECT_ASSIGNMENT_DECLINED'
          : typeFilter.value === 'INCIDENT'
            ? item.type === 'SIMULATION_INCIDENT' || item.type === 'SIMULATION_INCIDENT_RESOLVED'
            : item.type === 'REROUTE_CREATED' ||
            item.type === 'REROUTE_UNAVAILABLE' ||
            item.type === 'DRIVER_ROUTE_CHANGED');
      return (
        typeMatch && (severityFilter.value === 'ALL' || item.severity === severityFilter.value)
      );
    }),
  );
  const unread = computed(() => items.value.filter((item) => !item.readAt).length);
  async function read(item: NotificationItem) {
    if (busyId.value !== null || item.readAt) return;
    busyId.value = item.id;
    error.value = null;
    try {
      const updated = await markNotificationRead(item.id);
      if (!disposed) {
        if (updated.readAt) confirmedReadAt.set(updated.id, updated.readAt);
        items.value = items.value.map((row) => (row.id === updated.id ? updated : row));
        notifySuccess('Đã đánh dấu cảnh báo là đã đọc.');
      }
    } catch (reason) {
      if (!disposed)
        error.value = reason instanceof Error ? reason.message : 'Không thể cập nhật cảnh báo.';
    } finally {
      if (!disposed) busyId.value = null;
    }
  }
  async function readAll() {
    if (busyId.value !== null || unread.value === 0) return;
    const ids = new Set(items.value.map((item) => item.id));
    busyId.value = -1;
    error.value = null;
    try {
      await markAllNotificationsRead();
      if (!disposed) {
        const now = new Date().toISOString();
        ids.forEach((id) => confirmedReadAt.set(id, now));
        items.value = items.value.map((item) =>
          ids.has(item.id) && !item.readAt ? { ...item, readAt: now } : item,
        );
        notifySuccess('Đã đánh dấu tất cả cảnh báo là đã đọc.');
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
        confirmedDeletedIds.add(id);
        items.value = items.value.filter((item) => item.id !== id);
        confirmDelete.value = null;
        notifySuccess('Đã xóa thông báo.');
      }
    } catch (reason) {
      if (!disposed)
        deleteError.value = reason instanceof Error ? reason.message : 'Không thể xóa cảnh báo.';
    } finally {
      if (!disposed) busyId.value = null;
    }
  }
  async function updateIncident(item: NotificationItem) {
    const incidentId = item.simulationIncidentId;
    if (incidentId == null || busyId.value !== null) return;
    busyId.value = item.id;
    error.value = null;
    try {
      const response = await acknowledgeIncident(incidentId);
      if (!disposed) {
        items.value = items.value.map((row) => row.simulationIncidentId === incidentId
          ? { ...row, simulationIncidentStatus: response.status }
          : row);
        notifySuccess('Đã tiếp nhận sự cố.');
      }
    } catch (reason) {
      if (!disposed) error.value = reason instanceof Error ? reason.message : 'Không thể cập nhật sự cố.';
    } finally {
      if (!disposed) busyId.value = null;
    }
  }
  return {
    items,
    filtered,
    unread,
    loading,
    error,
    deleteError,
    typeFilter,
    severityFilter,
    busyId,
    confirmDelete,
    refresh: () => attempt.value++,
    read,
    readAll,
    remove,
    acknowledge: (item: NotificationItem) => updateIncident(item),
  };
}
