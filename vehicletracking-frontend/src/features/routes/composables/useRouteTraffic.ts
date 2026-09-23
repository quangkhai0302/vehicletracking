import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchHereIncidents, fetchHereTrafficFlow } from '@/features/traffic/api/hereTraffic';
import type { TrafficFlowResponse, TrafficIncidentsResponse } from '@/features/traffic/types/traffic';

export interface RouteTrafficData {
  key: string;
  flow: TrafficFlowResponse | null;
  incidents: TrafficIncidentsResponse | null;
  receivedAt: number;
  flowError: boolean;
  incidentError: boolean;
}

export function useRouteTraffic(key: MaybeRefOrGetter<string | null>) {
  const cache = new Map<string, RouteTrafficData>();
  const data = shallowRef<RouteTrafficData | null>(null), loadingKey = ref<string | null>(null), attempt = ref(0);
  watch([() => toValue(key), attempt], ([currentKey], _previous, cleanup) => {
    if (!currentKey) return;
    let alive = true, controller: AbortController | null = null, timer: number | null = null;
    const refresh = async () => {
      const cached = cache.get(currentKey);
      if (cached && !cached.flowError && !cached.incidentError && Date.now() - cached.receivedAt < 60_000) {
        data.value = cached; loadingKey.value = null;
        timer = window.setTimeout(() => void refresh(), Math.max(1000, 60_000 - (Date.now() - cached.receivedAt)));
        return;
      }
      controller = new AbortController(); loadingKey.value = currentKey;
      const [flow, incidents] = await Promise.allSettled([
        fetchHereTrafficFlow(currentKey, controller.signal), fetchHereIncidents(currentKey, controller.signal),
      ]);
      if (!alive) return;
      const next: RouteTrafficData = { key: currentKey,
        flow: flow.status === 'fulfilled' ? flow.value : null,
        incidents: incidents.status === 'fulfilled' ? incidents.value : null,
        flowError: flow.status === 'rejected', incidentError: incidents.status === 'rejected', receivedAt: Date.now() };
      cache.delete(currentKey); cache.set(currentKey, next);
      if (cache.size > 30) cache.delete(cache.keys().next().value!);
      data.value = next; loadingKey.value = null;
      timer = window.setTimeout(() => void refresh(), 60_000);
    };
    timer = window.setTimeout(() => void refresh(), 200);
    cleanup(() => { alive = false; controller?.abort(); if (timer !== null) window.clearTimeout(timer); });
  }, { immediate: true });
  return reactive({ data: computed(() => toValue(key) && data.value?.key === toValue(key) ? data.value : null),
    loading: computed(() => !!toValue(key) && (loadingKey.value === toValue(key) || data.value?.key !== toValue(key))),
    retry: () => { const currentKey = toValue(key); if (currentKey) cache.delete(currentKey); attempt.value++; } });
}
