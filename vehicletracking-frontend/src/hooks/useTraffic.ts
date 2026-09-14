import { useEffect, useState, useRef, type RefObject } from 'react';
import L from 'leaflet';
import { fetchHereIncidents } from '../services/hereTraffic';
import type { TrafficIncidentsResponse } from '../types/traffic';

// Snap coordinates to a 0.02-degree grid (~2 km) to reuse nearby viewport responses.
function snap(val: number): number {
  return Math.round(val * 50) / 50;
}

export function useTraffic(mapRef: RefObject<L.Map | null>, enabled: boolean, mapReady: boolean) {
  const [incidents, setIncidents] = useState<TrafficIncidentsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const lastFetchedBboxRef = useRef<string | null>(null);
  const lastFetchedBoundsRef = useRef<L.LatLngBounds | null>(null);

  useEffect(() => {
    const map = mapRef.current;
    if (!enabled || !mapReady || !map) return;
    let alive = true;
    let debounce: number | null = null;
    let controller: AbortController | null = null;
    let requestId = 0;

    const refresh = (force = false) => {
      if (!alive) return;
      const currentBounds = map.getBounds();

      // If already fetched and current viewport is still inside the fetched area, don't refetch!
      if (!force && lastFetchedBoundsRef.current && lastFetchedBoundsRef.current.contains(currentBounds)) {
        return;
      }

      if (controller) controller.abort();
      const currentRequest = ++requestId;
      controller = new AbortController();

      // Buffer around current view and snap to 0.02 grid:
      const west = snap(currentBounds.getWest() - 0.03);
      const south = snap(currentBounds.getSouth() - 0.03);
      const east = snap(currentBounds.getEast() + 0.03);
      const north = snap(currentBounds.getNorth() + 0.03);
      const bbox = `${west.toFixed(4)},${south.toFixed(4)},${east.toFixed(4)},${north.toFixed(4)}`;

      if (!force && bbox === lastFetchedBboxRef.current) {
        return;
      }

      setLoading(true);
      fetchHereIncidents(bbox, controller.signal).then(result => {
        if (!alive || currentRequest !== requestId) return;
        setIncidents(result);
        lastFetchedBboxRef.current = bbox;
        lastFetchedBoundsRef.current = L.latLngBounds([south, west], [north, east]);
        setError(null);
      }).catch(reason => {
        if (alive && currentRequest === requestId && reason?.name !== 'AbortError') {
          // Do not keep rendering an expired successful response as if it were
          // current traffic when the backend can no longer provide a stale
          // envelope. The next successful request will replace the layer.
          setIncidents(null);
          setError('Không tải được dữ liệu sự cố giao thông.');
        }
      }).finally(() => {
        if (alive && currentRequest === requestId) setLoading(false);
      });
    };

    const schedule = () => {
      if (debounce !== null) window.clearTimeout(debounce);
      debounce = window.setTimeout(() => refresh(false), 350);
    };

    map.on('moveend', schedule);
    refresh(true);

    const timer = window.setInterval(() => refresh(true), 60_000);

    return () => {
      alive = false;
      if (debounce !== null) window.clearTimeout(debounce);
      if (controller) controller.abort();
      window.clearInterval(timer);
      map.off('moveend', schedule);
    };
  }, [attempt, enabled, mapReady, mapRef]);

  return {
    incidents: enabled && mapReady ? incidents : null,
    loading: enabled && mapReady && loading,
    error: enabled && mapReady ? error : null,
    refresh: () => {
      lastFetchedBboxRef.current = null;
      lastFetchedBoundsRef.current = null;
      setAttempt(v => v + 1);
    },
  };
}
