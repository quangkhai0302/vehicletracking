import { useEffect, useRef, useState } from 'react';
import { fetchTrip } from '../services/fleet';
import { controlSimulation } from '../services/operations';
import type { TripDetail } from '../types/fleet';
import type { OperationsSnapshot, SimulationAction, SimulationRun } from '../types/operations';

export function useSimulator(snapshot: OperationsSnapshot | null, onToast: (message: string) => void) {
  const [tripId, setTripId] = useState<number | null>(null);
  const [loadedDetail, setLoadedDetail] = useState<TripDetail | null>(null);
  const [localRun, setLocalRun] = useState<SimulationRun | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const busyRef = useRef(false);
  const mounted = useRef(false);
  const replayNumber = Math.max(snapshot?.trips.find(trip => trip.id === tripId)?.attemptNumber ?? 1,
    localRun?.tripId === tripId ? localRun.attemptNumber ?? 1 : 1);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    if (tripId === null) return;
    const controller = new AbortController();
    fetchTrip(tripId, controller.signal).then(data => {
      if (!controller.signal.aborted) { setLoadedDetail(data); setError(null); }
    }).catch((err: unknown) => {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không tải được tuyến chuyến.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tripId, attempt, replayNumber]);
  const select = (id: number | null) => {
    if (busyRef.current) return;
    setTripId(id); setLoadedDetail(null); setLocalRun(null); setError(null); setLoading(id !== null); setAttempt(value => value + 1);
  };
  const retry = () => { if (!busyRef.current) { setLoading(true); setError(null); setAttempt(value => value + 1); } };
  const remoteRun = snapshot?.simulations.find(run => run.tripId === tripId) ?? null;
  const run = localRun?.tripId === tripId && (!remoteRun || (localRun.attemptNumber ?? 1) > (remoteRun.attemptNumber ?? 1)
    || ((localRun.attemptNumber ?? 1) === (remoteRun.attemptNumber ?? 1) && Date.parse(localRun.updatedAt) > Date.parse(remoteRun.updatedAt))) ? localRun : remoteRun;
  const detail = loadedDetail?.trip.id === tripId && (loadedDetail.trip.attemptNumber ?? 1) === replayNumber ? loadedDetail : null;
  const remoteTrip = snapshot?.trips.find(item => item.id === tripId);
  const trip = remoteTrip && (remoteTrip.attemptNumber ?? 1) === replayNumber ? remoteTrip : detail?.trip ?? null;
  const command = async (action: SimulationAction, multiplier?: 1 | 5 | 10) => {
    if (!tripId || busyRef.current) return false;
    busyRef.current = true; setBusy(true); setError(null);
    try {
      const response = await controlSimulation(tripId, action, multiplier);
      if (!mounted.current) return true;
      setLocalRun(response);
      if (response.tripId !== tripId) { setTripId(response.tripId); setLoadedDetail(null); setLoading(true); }
      if (action === 'reset') { setLoadedDetail(null); setLoading(true); setAttempt(value => value + 1); }
      onToast(action === 'reset' ? `Đã đặt lại chuyến #${response.tripId}. Bấm Bắt đầu để chạy lại; lịch sử cũ được giữ.` :
        action === 'stop' ? 'Đã dừng mô phỏng và hủy chuyến.' : action === 'pause' ? 'Đã tạm dừng mô phỏng.' :
        action === 'play' ? 'Đang mô phỏng theo tuyến đã lưu.' : `Tốc độ phát ${multiplier}×.`);
      return true;
    } catch (err) {
      if (mounted.current) setError(err instanceof Error ? err.message : 'Điều khiển chưa thành công.');
      return false;
    } finally { busyRef.current = false; if (mounted.current) setBusy(false); }
  };
  return { tripId, trip, detail, run, loading, busy, error, select, retry, command };
}
