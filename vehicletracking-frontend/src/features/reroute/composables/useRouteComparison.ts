import { ref, shallowRef, watch } from 'vue';
import { fetchRouteComparison } from '../api/comparison';
import type { RouteComparison } from '../types/comparison';
export function useRouteComparison(tripId: () => number, revisionId: () => number) {
  const data = shallowRef<RouteComparison | null>(null), loading = ref(false), error = ref<string | null>(null), retryToken = ref(0);
  watch([tripId, revisionId, retryToken], async ([trip, revision], _old, cleanup) => {
    const controller = new AbortController();
    cleanup(() => controller.abort());
    data.value = null; error.value = null; loading.value = true;
    try {
      const result = await fetchRouteComparison(trip, revision, controller.signal);
      if (!controller.signal.aborted) data.value = result;
    } catch (failure) {
      if (!controller.signal.aborted) error.value = failure instanceof Error ? failure.message : 'Chưa tải được lịch sử đổi tuyến.';
    } finally {
      if (!controller.signal.aborted) loading.value = false;
    }
  }, { immediate: true });
  return { data, loading, error, retry: () => retryToken.value++ };
}
