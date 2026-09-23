import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter, type ShallowRef } from 'vue';
import L from 'leaflet';
import { fetchHereIncidents, fetchHereTrafficFlow } from '@/features/traffic/api/hereTraffic';
import type { TrafficFlowResponse, TrafficIncidentsResponse } from '@/features/traffic/types/traffic';

const snap = (value: number) => Math.round(value * 50) / 50;

export function useTraffic(mapRef: ShallowRef<L.Map | null>, enabled: MaybeRefOrGetter<boolean>, mapReady: MaybeRefOrGetter<boolean>) {
  const flow = shallowRef<TrafficFlowResponse | null>(null), incidents = shallowRef<TrafficIncidentsResponse | null>(null);
  const loading = ref(false), error = ref<string | null>(null), attempt = ref(0);
  let lastFetchedBbox: string | null = null, lastFetchedBounds: L.LatLngBounds | null = null;
  const clearBounds = () => { lastFetchedBbox = null; lastFetchedBounds = null; };
  watch([mapRef, () => toValue(enabled), () => toValue(mapReady), attempt], ([map, active, ready], _old, cleanup) => {
    if (!active || !ready || !map) return;
    let alive = true, debounce: number | null = null, controller: AbortController | null = null, requestId = 0;
    const refresh = (force = false) => {
      if (!alive) return;
      const currentBounds = map.getBounds();
      if (!force && lastFetchedBounds?.contains(currentBounds)) return;
      controller?.abort();
      const currentRequest = ++requestId;
      controller = new AbortController();
      const west = Math.max(-180, snap(currentBounds.getWest() - 0.03));
      const south = Math.max(-90, snap(currentBounds.getSouth() - 0.03));
      const east = Math.min(180, snap(currentBounds.getEast() + 0.03));
      const north = Math.min(90, snap(currentBounds.getNorth() + 0.03));
      if (west >= east || south >= north || east - west > 1 || north - south > 1) {
        flow.value = null; incidents.value = null; loading.value = false;
        error.value = 'Phóng to bản đồ để xem sự cố giao thông.'; clearBounds(); return;
      }
      const bbox = `${west.toFixed(4)},${south.toFixed(4)},${east.toFixed(4)},${north.toFixed(4)}`;
      if (!force && bbox === lastFetchedBbox) return;
      loading.value = true;
      Promise.allSettled([fetchHereTrafficFlow(bbox, controller.signal), fetchHereIncidents(bbox, controller.signal)])
        .then(([flowResult, incidentResult]) => {
          if (!alive || currentRequest !== requestId) return;
          const flowFailed = flowResult.status === 'rejected', incidentsFailed = incidentResult.status === 'rejected';
          flow.value = flowResult.status === 'fulfilled' ? flowResult.value : null;
          incidents.value = incidentResult.status === 'fulfilled' ? incidentResult.value : null;
          if (flowFailed || incidentsFailed) clearBounds();
          else { lastFetchedBbox = bbox; lastFetchedBounds = L.latLngBounds([south, west], [north, east]); }
          error.value = flowFailed && incidentsFailed ? 'Không tải được dữ liệu giao thông.'
            : flowFailed ? 'Không tải được luồng giao thông.' : incidentsFailed ? 'Không tải được sự cố giao thông.' : null;
        }).finally(() => { if (alive && currentRequest === requestId) loading.value = false; });
    };
    const schedule = () => {
      if (debounce !== null) window.clearTimeout(debounce);
      debounce = window.setTimeout(() => refresh(false), 350);
    };
    map.on('moveend', schedule); refresh(true);
    const timer = window.setInterval(() => refresh(true), 60_000);
    cleanup(() => {
      alive = false; controller?.abort(); map.off('moveend', schedule);
      if (debounce !== null) window.clearTimeout(debounce);
      window.clearInterval(timer);
    });
  }, { immediate: true });
  const visible = () => toValue(enabled) && toValue(mapReady);
  return reactive({ flow: computed(() => visible() ? flow.value : null), incidents: computed(() => visible() ? incidents.value : null),
    loading: computed(() => visible() && loading.value), error: computed(() => visible() ? error.value : null),
    refresh: () => { clearBounds(); attempt.value++; } });
}
