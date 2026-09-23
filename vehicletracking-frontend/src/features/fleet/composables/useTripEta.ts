import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchTripEta } from '@/features/fleet/api/eta';
import type { TripEta } from '@/features/fleet/types/eta';
export function useTripEta(tripId: MaybeRefOrGetter<number | null>) {
  const data = shallowRef<TripEta | null>(null), loading = ref(false), error = shallowRef<{ tripId: number; message: string } | null>(null);
  const loadedTripId = ref<number | null>(null), attempt = ref(0);
  watch([() => toValue(tripId), attempt], ([id], _old, cleanup) => {
    if (id === null) return;
    let alive = true, active: AbortController | null = null, timer: number | undefined;
    function load() {
      const controller = new AbortController(); active = controller; loading.value = true;
      fetchTripEta(id!, controller.signal).then(result => {
        if (alive) { data.value = result; loadedTripId.value = id; error.value = null; }
      }).catch(reason => { if (alive && !controller.signal.aborted) error.value = { tripId: id!, message: reason instanceof Error ? reason.message : 'Không tải được ETA theo traffic.' }; })
        .finally(() => { if (alive) { loading.value = false; timer = window.setTimeout(load, 10_000); } });
    }
    load(); cleanup(() => { alive = false; active?.abort(); window.clearTimeout(timer); });
  }, { immediate: true });
  return reactive({ data: computed(() => toValue(tripId) !== null && loadedTripId.value === toValue(tripId) ? data.value : null),
    loading: computed(() => toValue(tripId) !== null && loading.value), error: computed(() => error.value?.tripId === toValue(tripId) ? error.value?.message ?? null : null), retry: () => { attempt.value++; } });
}
