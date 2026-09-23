import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchTripCheckIns } from '@/features/fleet/api/checkins';
import type { TripCheckIns } from '@/features/fleet/types/checkin';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
export function useTripCheckIns(tripId: MaybeRefOrGetter<number | null>, snapshot?: MaybeRefOrGetter<OperationsSnapshot | null>) {
  const live = computed(() => { const id = toValue(tripId), current = toValue(snapshot); return id === null || !Array.isArray(current?.checkIns) ? null : current.checkIns.find(item => item.tripId === id) ?? null; });
  const data = shallowRef<TripCheckIns | null>(live.value), loading = ref(toValue(tripId) !== null), error = ref<string | null>(null), attempt = ref(0);
  watch([() => toValue(tripId), attempt], ([id], _old, cleanup) => {
    if (id === null) return;
    const controller = new AbortController();
    fetchTripCheckIns(id, controller.signal).then(value => { if (!controller.signal.aborted) data.value = value; })
      .catch(reason => { if (!controller.signal.aborted) error.value = reason instanceof Error ? reason.message : 'Không thể tải dữ liệu check-in.'; })
      .finally(() => { if (!controller.signal.aborted) loading.value = false; });
    cleanup(() => controller.abort());
  }, { immediate: true });
  const effective = computed(() => live.value && (!data.value || data.value.tripId !== toValue(tripId) || live.value.revision >= data.value.revision) ? live.value : data.value);
  return reactive({ data: computed(() => toValue(tripId) === null ? null : effective.value?.tripId === toValue(tripId) ? effective.value : null),
    loading: computed(() => toValue(tripId) !== null && (loading.value || !effective.value || effective.value.tripId !== toValue(tripId))), error,
    retry: () => { error.value = null; loading.value = true; attempt.value++; } });
}
