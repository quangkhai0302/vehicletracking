import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchTrip, fetchTripRoute } from '@/features/fleet/api/fleet';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import type { TripDetail, TripStop, TripSummary } from '@/features/fleet/types/fleet';
import type { RouteDetail } from '@/features/routes/types/route';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { simulationFleetTrips, waitingSimulationTrips } from '@/features/simulation/utils/simulationFleet';

export interface WaitingSimulationVehicle { trip: TripSummary; start: TripStop }
export interface SimulationFleetRoute { trip: TripSummary; route: RouteDetail; segments: [number, number][][] }

function prepareDetail(detail: TripDetail) {
  const first = [...detail.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber)[0];
  const valid = ([lat, lng]: [number, number]) => Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
  const start = first && valid([first.latitude, first.longitude]) ? first : null;
  let segments: [number, number][][] | null = null;
  try {
    const decoded = detail.route.sections.map(section => decodeFlexiblePolyline(section.encodedPolyline));
    if (decoded.length && decoded.every(points => points.length >= 2 && points.every(valid))) segments = decoded;
  } catch { /* Keep a valid first stop even if route geometry is invalid. */ }
  return { route: detail.route, start, segments };
}

export function useSimulationFleet(snapshot: MaybeRefOrGetter<OperationsSnapshot | null>, enabled: MaybeRefOrGetter<boolean>) {
  const trips = computed(() => simulationFleetTrips(toValue(snapshot)));
  const waiting = computed(() => waitingSimulationTrips(toValue(snapshot), trips.value));
  const tripKey = (trip: TripSummary) => {
    const run = toValue(snapshot)?.simulations.find(item => item.tripId === trip.id);
    return `${trip.id}:${trip.routeId}:${run?.attemptNumber ?? 1}:${run?.routeRevisionId ?? 0}`;
  };
  const key = computed(() => toValue(enabled) ? trips.value.map(tripKey).sort().join(',') : '');
  const details = shallowRef<Record<string, ReturnType<typeof prepareDetail>>>({});
  const errors = ref<Record<number, string>>({}), attempt = ref(0);
  const cache = new Map<string, ReturnType<typeof prepareDetail>>();
  watch([key, attempt], ([currentKey], _previous, cleanup) => {
    if (!currentKey) return;
    const keys = currentKey.split(','), wanted = new Set(keys), controller = new AbortController();
    for (const id of cache.keys()) if (!wanted.has(id)) cache.delete(id);
    let cursor = 0;
    const worker = async () => {
      while (cursor < keys.length && !controller.signal.aborted) {
        const itemKey = keys[cursor++], id = Number(itemKey.split(':')[0]);
        if (cache.has(itemKey)) continue;
        try {
          const detail = await fetchTrip(id, controller.signal);
          if (itemKey.split(':')[3] !== '0') detail.route = await fetchTripRoute(id, controller.signal);
          if (controller.signal.aborted) return;
          cache.set(itemKey, prepareDetail(detail)); details.value = Object.fromEntries(cache);
          delete errors.value[id];
        } catch (error) {
          if (!controller.signal.aborted) errors.value[id] = error instanceof Error ? error.message : 'Không tải được lộ trình.';
        }
      }
    };
    void Promise.all(Array.from({ length: Math.min(4, keys.length) }, worker));
    cleanup(() => controller.abort());
  }, { immediate: true });
  const loaded = (trip: TripSummary) => details.value[tripKey(trip)];
  const previews = computed<WaitingSimulationVehicle[]>(() => toValue(enabled) ? waiting.value.flatMap(trip => {
    const start = loaded(trip)?.start; return start ? [{ trip, start }] : [];
  }) : []);
  const failures = computed(() => toValue(enabled) ? waiting.value.filter(trip => (errors.value[trip.id] || loaded(trip)) && !loaded(trip)?.start)
    .map(trip => ({ trip, message: errors.value[trip.id] ?? 'Chuyến chưa có tọa độ trạm đầu hợp lệ.' })) : []);
  const routes = computed<SimulationFleetRoute[]>(() => toValue(enabled) ? trips.value.flatMap(trip => {
    const data = loaded(trip); return data?.segments ? [{ trip, route: data.route, segments: data.segments }] : [];
  }) : []);
  const routeFailures = computed(() => toValue(enabled) ? trips.value.filter(trip => (errors.value[trip.id] || loaded(trip)) && !loaded(trip)?.segments)
    .map(trip => ({ trip, message: errors.value[trip.id] ?? 'Hình học tuyến không hợp lệ.' })) : []);
  return reactive({ trips, previews, failures, routes, routeFailures,
    loading: computed(() => toValue(enabled) && trips.value.some(trip => !loaded(trip) && !errors.value[trip.id])),
    retry: () => {
      for (const [id, data] of cache) if (!data.start || !data.segments) cache.delete(id);
      errors.value = {}; attempt.value++;
    } });
}
