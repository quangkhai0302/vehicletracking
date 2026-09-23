import { computed, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchTripRoute } from '@/features/fleet/api/fleet';
import type { RouteDetail } from '@/features/routes/types/route';

/** The visible route belongs to the selected trip and its current replay/revision. */
export function useSelectedVehicleRoute(tripId: MaybeRefOrGetter<number | null>, onError: (message: string) => void,
  revisionKey: MaybeRefOrGetter<string> = '') {
  const detail = shallowRef<{ tripId: number; route: RouteDetail; revisionKey: string } | null>(null);
  watch([() => toValue(tripId), () => toValue(revisionKey)], ([id, revision], _old, cleanup) => {
    if (id === null) return;
    const controller = new AbortController();
    fetchTripRoute(id, controller.signal).then(route => {
      if (!controller.signal.aborted) detail.value = { tripId: id, route, revisionKey: revision };
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) {
        detail.value = null;
        onError(error instanceof Error ? error.message : 'Không tải được tuyến của xe đang chọn.');
      }
    });
    cleanup(() => controller.abort());
  }, { immediate: true });
  return computed(() => toValue(tripId) !== null && detail.value?.tripId === toValue(tripId)
    && detail.value.revisionKey === toValue(revisionKey) ? detail.value.route : null);
}
