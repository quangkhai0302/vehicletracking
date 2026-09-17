import { useCallback, useEffect, useState } from 'react';
import { fetchTripEta } from '../services/eta';
import type { TripEta } from '../types/eta';

const REFRESH_MS = 10_000;

export function useTripEta(tripId: number | null) {
  const [data, setData] = useState<TripEta | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<{ tripId: number; message: string } | null>(null);
  const [loadedTripId, setLoadedTripId] = useState<number | null>(null);
  const [attempt, setAttempt] = useState(0);

  const retry = useCallback(() => setAttempt(value => value + 1), []);

  useEffect(() => {
    if (tripId === null) {
      return;
    }
    let alive = true;
    let requestId = 0;
    let activeController: AbortController | null = null;
    let timer: number | undefined;
    const load = () => {
      const controller = new AbortController();
      activeController = controller;
      const currentRequest = ++requestId;
      setLoading(true);
      fetchTripEta(tripId, controller.signal).then(result => {
        if (alive && currentRequest === requestId) { setData(result); setLoadedTripId(tripId); setError(null); }
      }).catch((reason: unknown) => {
        if (alive && currentRequest === requestId && !controller.signal.aborted) setError({ tripId, message: reason instanceof Error ? reason.message : 'Không tải được ETA theo traffic.' });
      }).finally(() => {
        if (alive && currentRequest === requestId) {
          setLoading(false);
          timer = window.setTimeout(load, REFRESH_MS);
        }
      });
    };
    load();
    return () => { alive = false; activeController?.abort(); window.clearTimeout(timer); };
  }, [tripId, attempt]);

  return { data: tripId !== null && loadedTripId === tripId ? data : null,
    loading: tripId !== null && loading, error: error?.tripId === tripId ? error.message : null, retry };
}
