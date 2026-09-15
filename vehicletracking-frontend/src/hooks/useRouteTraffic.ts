import { useEffect, useRef, useState } from 'react';
import { fetchHereIncidents, fetchHereTrafficFlow } from '../services/hereTraffic';
import type { TrafficFlowResponse, TrafficIncidentsResponse } from '../types/traffic';

export interface RouteTrafficData {
  key: string;
  flow: TrafficFlowResponse | null;
  incidents: TrafficIncidentsResponse | null;
  receivedAt: number;
  flowError: boolean;
  incidentError: boolean;
}

export function useRouteTraffic(key: string | null) {
  const cache = useRef(new Map<string, RouteTrafficData>());
  const [data, setData] = useState<RouteTrafficData | null>(null);
  const [loadingKey, setLoadingKey] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!key) return;
    let alive = true;
    let controller: AbortController | null = null;
    let timer: number | null = null;
    const refresh = async () => {
      const cached = cache.current.get(key);
      if (cached && !cached.flowError && !cached.incidentError && Date.now() - cached.receivedAt < 60_000) {
        setData(cached);
        setLoadingKey(null);
        timer = window.setTimeout(() => void refresh(), Math.max(1000, 60_000 - (Date.now() - cached.receivedAt)));
        return;
      }
      controller = new AbortController();
      setLoadingKey(key);
      const [flow, incidents] = await Promise.allSettled([
        fetchHereTrafficFlow(key, controller.signal), fetchHereIncidents(key, controller.signal),
      ]);
      if (!alive) return;
      const next: RouteTrafficData = { key,
        flow: flow.status === 'fulfilled' ? flow.value : null,
        incidents: incidents.status === 'fulfilled' ? incidents.value : null,
        flowError: flow.status === 'rejected', incidentError: incidents.status === 'rejected', receivedAt: Date.now() };
      // Replace failed responses, never retain a previous success with a live badge.
      cache.current.delete(key);
      cache.current.set(key, next);
      if (cache.current.size > 30) cache.current.delete(cache.current.keys().next().value!);
      setData(next);
      setLoadingKey(null);
      timer = window.setTimeout(() => void refresh(), 60_000);
    };
    // A short dwell avoids network work when merely crossing a route with the cursor.
    timer = window.setTimeout(() => void refresh(), 200);
    return () => { alive = false; controller?.abort(); if (timer !== null) window.clearTimeout(timer); };
  }, [key, attempt]);
  return { data: key && data?.key === key ? data : null, loading: !!key && (loadingKey === key || data?.key !== key),
    retry: () => { if (key) cache.current.delete(key); setAttempt(value => value + 1); } };
}
