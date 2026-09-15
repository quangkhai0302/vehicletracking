import { useEffect, useMemo, useRef, useState } from 'react';
import { fetchTrip } from '../services/fleet';
import type { TripDetail, TripSummary } from '../types/fleet';
import type { OperationsSnapshot } from '../types/operations';
import type { VehicleMarkerAnchor } from './useVehicleMarkers';

function plannedTrips(snapshot: OperationsSnapshot | null): TripSummary[] {
  if (!snapshot) return [];
  const tripById = new Map(snapshot.trips.map(trip => [trip.id, trip]));
  const currentVehicles = new Set(snapshot.positions.filter(position => {
    const trip = tripById.get(position.tripId);
    // Keep a scheduled trip visible when the only position for its vehicle is
    // an old completed/cancelled trip. Unknown trips are treated as current.
    return !trip || trip.status === 'IN_PROGRESS';
  }).map(position => position.vehicleId));
  const observedTrips = new Set(snapshot.positions.map(position => position.tripId));
  const selectedVehicles = new Set<number>();
  return [...snapshot.trips]
    .filter(trip => trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS')
    .sort((a, b) => {
      const statusOrder = (trip: TripSummary) => trip.status === 'IN_PROGRESS' ? 0 : 1;
      return statusOrder(a) - statusOrder(b)
        || Date.parse(a.scheduledDepartureAt) - Date.parse(b.scheduledDepartureAt)
        || a.id - b.id;
    })
    .filter(trip => {
      if (observedTrips.has(trip.id) || currentVehicles.has(trip.vehicleId) || selectedVehicles.has(trip.vehicleId)) return false;
      selectedVehicles.add(trip.vehicleId);
      return true;
    });
}

function firstStop(detail: TripDetail | undefined) {
  if (!detail) return null;
  const stop = [...detail.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber)[0];
  return stop && Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude)
    && Math.abs(stop.latitude) <= 90 && Math.abs(stop.longitude) <= 180 ? stop : null;
}

/** Loads persisted trip starts so scheduled vehicles are visible after refresh, before telemetry exists. */
export function usePlannedVehicleAnchors(snapshot: OperationsSnapshot | null): VehicleMarkerAnchor[] {
  const trips = useMemo(() => plannedTrips(snapshot), [snapshot]);
  const key = trips.map(trip => `${trip.id}:${trip.routeId}`).join(',');
  const cache = useRef(new Map<string, TripDetail>());
  const [details, setDetails] = useState<Record<string, TripDetail>>({});

  useEffect(() => {
    if (!key) {
      cache.current.clear();
      return;
    }
    const wanted = new Set(key.split(','));
    for (const cachedKey of cache.current.keys()) if (!wanted.has(cachedKey)) cache.current.delete(cachedKey);
    const controller = new AbortController();
    const requestedTrips = key.split(',').map(token => ({
      cacheKey: token,
      id: Number(token.split(':')[0]),
    }));
    let cursor = 0;
    const worker = async () => {
      while (cursor < requestedTrips.length && !controller.signal.aborted) {
        const { cacheKey: tripKey, id } = requestedTrips[cursor++];
        if (cache.current.has(tripKey)) continue;
        try {
          const detail = await fetchTrip(id, controller.signal);
          if (controller.signal.aborted) return;
          cache.current.set(tripKey, detail);
          setDetails(Object.fromEntries(cache.current));
        } catch {
          // A transient detail failure should not hide other vehicles. The next
          // operations snapshot change will retry this trip if it is still eligible.
        }
      }
    };
    void Promise.all(Array.from({ length: Math.min(4, requestedTrips.length) }, worker));
    return () => controller.abort();
  }, [key]);

  return trips.flatMap(trip => {
    const stop = firstStop(details[`${trip.id}:${trip.routeId}`]);
    return stop ? [{ vehicleId: trip.vehicleId, tripId: trip.id, vehiclePlateNumber: trip.vehiclePlateNumber,
      vehicleType: trip.vehicleType, latitude: stop.latitude, longitude: stop.longitude }] : [];
  });
}
