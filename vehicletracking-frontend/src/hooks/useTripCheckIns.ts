import { useEffect, useState } from 'react';
import { fetchTripCheckIns } from '../services/checkins';
import type { TripCheckIns } from '../types/checkin';
import type { OperationsSnapshot } from '../types/operations';

export function useTripCheckIns(tripId: number | null, snapshot?: OperationsSnapshot | null) {
  const live = tripId === null || !Array.isArray(snapshot?.checkIns) ? null : snapshot.checkIns.find(item => item.tripId === tripId) ?? null;
  const [data, setData] = useState<TripCheckIns | null>(live);
  const [loading, setLoading] = useState(tripId !== null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (tripId === null) return;
    const controller = new AbortController();
    fetchTripCheckIns(tripId, controller.signal).then(value => {
      if (!controller.signal.aborted) setData(value);
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải dữ liệu check-in.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tripId, attempt]);
  const effective = live && (!data || data.tripId !== tripId || live.revision >= data.revision) ? live : data;
  return { data: tripId === null ? null : effective?.tripId === tripId ? effective : null,
    loading: tripId !== null && (loading || !effective || effective.tripId !== tripId), error,
    retry: () => { setError(null); setLoading(true); setAttempt(value => value + 1); } };
}
