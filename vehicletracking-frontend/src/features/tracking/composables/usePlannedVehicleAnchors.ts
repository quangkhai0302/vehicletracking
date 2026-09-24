import { computed, shallowRef, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { fetchTrip } from '@/features/fleet/api/fleet';
import type { TripDetail, TripSummary, VehicleType } from '@/features/fleet/types/fleet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { tripReferenceTime } from '@/features/fleet/utils/tripTime';

export interface VehicleMarkerAnchor {
  vehicleId: number;
  tripId: number;
  vehiclePlateNumber: string;
  vehicleType: VehicleType;
  latitude: number;
  longitude: number;
}

function plannedTrips(snapshot: OperationsSnapshot | null): TripSummary[] {
  if (!snapshot) return [];
  const tripById = new Map(snapshot.trips.map((trip) => [trip.id, trip]));
  const currentVehicles = new Set(
    snapshot.positions
      .filter((position) => {
        const trip = tripById.get(position.tripId);
        return !trip || trip.status === 'IN_PROGRESS';
      })
      .map((position) => position.vehicleId),
  );
  const observedTrips = new Set(snapshot.positions.map((position) => position.tripId)),
    selectedVehicles = new Set<number>();
  return [...snapshot.trips]
    .filter((trip) => trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS')
    .sort((a, b) => {
      const order = (trip: TripSummary) => (trip.status === 'IN_PROGRESS' ? 0 : 1);
      return (
        order(a) - order(b) ||
        Date.parse(tripReferenceTime(a)) - Date.parse(tripReferenceTime(b)) ||
        a.id - b.id
      );
    })
    .filter((trip) => {
      if (
        observedTrips.has(trip.id) ||
        currentVehicles.has(trip.vehicleId) ||
        selectedVehicles.has(trip.vehicleId)
      )
        return false;
      selectedVehicles.add(trip.vehicleId);
      return true;
    });
}

function firstStop(detail: TripDetail | undefined) {
  const stop = detail && [...detail.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber)[0];
  return stop &&
    Number.isFinite(stop.latitude) &&
    Number.isFinite(stop.longitude) &&
    Math.abs(stop.latitude) <= 90 &&
    Math.abs(stop.longitude) <= 180
    ? stop
    : null;
}

export function usePlannedVehicleAnchors(snapshot: MaybeRefOrGetter<OperationsSnapshot | null>) {
  const trips = computed(() => plannedTrips(toValue(snapshot)));
  const key = computed(() => trips.value.map((trip) => `${trip.id}:${trip.routeId}`).join(','));
  const cache = new Map<string, TripDetail>(),
    details = shallowRef<Record<string, TripDetail>>({});
  watch(
    key,
    (currentKey, _previous, cleanup) => {
      if (!currentKey) {
        cache.clear();
        details.value = {};
        return;
      }
      const wanted = new Set(currentKey.split(','));
      for (const cachedKey of cache.keys()) if (!wanted.has(cachedKey)) cache.delete(cachedKey);
      const controller = new AbortController();
      const requested = [...wanted].map((token) => ({
        key: token,
        id: Number(token.split(':')[0]),
      }));
      let cursor = 0;
      const worker = async () => {
        while (cursor < requested.length && !controller.signal.aborted) {
          const { key: tripKey, id } = requested[cursor++];
          if (cache.has(tripKey)) continue;
          try {
            const detail = await fetchTrip(id, controller.signal);
            if (controller.signal.aborted) return;
            cache.set(tripKey, detail);
            details.value = Object.fromEntries(cache);
          } catch {
            /* A failed detail must not hide the other vehicles. */
          }
        }
      };
      void Promise.all(Array.from({ length: Math.min(4, requested.length) }, worker));
      cleanup(() => controller.abort());
    },
    { immediate: true },
  );
  return computed<VehicleMarkerAnchor[]>(() =>
    trips.value.flatMap((trip) => {
      const stop = firstStop(details.value[`${trip.id}:${trip.routeId}`]);
      return stop
        ? [
            {
              vehicleId: trip.vehicleId,
              tripId: trip.id,
              vehiclePlateNumber: trip.vehiclePlateNumber,
              vehicleType: trip.vehicleType,
              latitude: stop.latitude,
              longitude: stop.longitude,
            },
          ]
        : [];
    }),
  );
}
