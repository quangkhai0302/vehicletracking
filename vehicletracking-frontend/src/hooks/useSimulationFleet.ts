import { useEffect, useMemo, useRef, useState } from 'react';
import { fetchTrip, fetchTripRoute } from '../services/fleet';
import { decodeFlexiblePolyline } from '../services/polyline';
import type { TripDetail, TripStop, TripSummary } from '../types/fleet';
import type { RouteDetail } from '../types/route';
import type { OperationsSnapshot } from '../types/operations';
import { simulationFleetTrips, waitingSimulationTrips } from '../utils/simulationFleet';

export interface WaitingSimulationVehicle { trip: TripSummary; start: TripStop }
export interface SimulationFleetRoute { trip: TripSummary; route: RouteDetail; segments: [number, number][][] }

function prepareDetail(detail: TripDetail) {
  const first = [...detail.stops].sort((a,b) => a.sequenceNumber-b.sequenceNumber)[0];
  const valid = ([lat,lng]: [number,number]) => Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat)<=90 && Math.abs(lng)<=180;
  const start = first && valid([first.latitude,first.longitude]) ? first : null;
  let segments: [number,number][][] | null = null;
  try {
    const decoded = detail.route.sections.map(section => decodeFlexiblePolyline(section.encodedPolyline));
    if (decoded.length && decoded.every(points => points.length>=2 && points.every(valid))) segments=decoded;
  } catch { /* Reject the entire route; retain a valid start marker independently. */ }
  return { route: detail.route, start, segments };
}

export function useSimulationFleet(snapshot: OperationsSnapshot | null, enabled: boolean) {
  const trips = useMemo(() => simulationFleetTrips(snapshot), [snapshot]);
  const waiting = useMemo(() => waitingSimulationTrips(snapshot, trips), [snapshot, trips]);
  const tripKey = (trip: TripSummary) => {
    const run = snapshot?.simulations.find(item => item.tripId === trip.id);
    return `${trip.id}:${trip.routeId}:${run?.attemptNumber ?? 1}:${run?.routeRevisionId ?? 0}`;
  };
  const key = enabled ? trips.map(tripKey).sort().join(',') : '';
  const [details, setDetails] = useState<Record<string, ReturnType<typeof prepareDetail>>>({});
  const [errors, setErrors] = useState<Record<number, string>>({});
  const cache = useRef(new Map<string, ReturnType<typeof prepareDetail>>());
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!key) return;
    const keys = key.split(',');
    const controller = new AbortController();
    const wanted = new Set(keys);
    for (const id of cache.current.keys()) if (!wanted.has(id)) cache.current.delete(id);
    let cursor = 0;
    const worker = async () => {
      while (cursor < keys.length && !controller.signal.aborted) {
        const itemKey = keys[cursor++];
        const id = Number(itemKey.split(':')[0]);
        if (cache.current.has(itemKey)) continue;
        try {
          const detail = await fetchTrip(id, controller.signal);
          if (itemKey.split(':')[3] !== '0') detail.route = await fetchTripRoute(id, controller.signal);
          if (controller.signal.aborted) return;
          cache.current.set(itemKey, prepareDetail(detail));
          setDetails(Object.fromEntries(cache.current));
          setErrors(previous => { const next = {...previous}; delete next[id]; return next; });
        } catch (error) {
          if (!controller.signal.aborted) setErrors(previous => ({...previous, [id]: error instanceof Error ? error.message : 'Không tải được lộ trình.'}));
        }
      }
    };
    void Promise.all(Array.from({length: Math.min(4, keys.length)}, worker));
    return () => controller.abort();
  }, [key, attempt]);
  const loaded = (trip: TripSummary) => details[tripKey(trip)];
  const previews: WaitingSimulationVehicle[] = enabled ? waiting.flatMap(trip => {
    const start = loaded(trip)?.start;
    return start ? [{trip,start}] : [];
  }) : [];
  const failures = enabled ? waiting.filter(trip => (errors[trip.id] || loaded(trip)) && !loaded(trip)?.start)
    .map(trip => ({trip, message: errors[trip.id] ?? 'Chuyến chưa có tọa độ trạm đầu hợp lệ.'})) : [];
  const routes: SimulationFleetRoute[] = enabled ? trips.flatMap(trip => {
    const data=loaded(trip);
    return data?.segments ? [{trip,route:data.route,segments:data.segments}] : [];
  }) : [];
  const routeFailures = enabled ? trips.filter(trip => (errors[trip.id] || loaded(trip)) && !loaded(trip)?.segments)
    .map(trip => ({trip, message: errors[trip.id] ?? 'Hình học tuyến không hợp lệ.'})) : [];
  return { trips, previews, failures, routes, routeFailures, loading: enabled && trips.some(trip => !loaded(trip) && !errors[trip.id]),
    retry: () => {
      for (const [id,data] of cache.current) if (!data.start || !data.segments) cache.current.delete(id);
      setErrors({}); setAttempt(value => value + 1);
    } };
}
