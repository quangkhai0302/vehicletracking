import type { OperationsSnapshot, SimulationRun, SimulationAction } from '../types/operations';
const BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, options);
  if (!response.ok) {
    let message = `Yêu cầu chưa thành công (HTTP ${response.status}).`;
    try { const problem = await response.json() as { detail?: string }; message = problem.detail || message; } catch { /* Use HTTP fallback. */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
export const fetchOperations = (signal?: AbortSignal) => request<OperationsSnapshot>('/telemetry/snapshot', { signal }).then(snapshot => {
  if (!Array.isArray(snapshot.checkIns)) throw new Error('Backend chưa hỗ trợ dữ liệu check-in của chuyến (cần nâng cấp backend).');
  return { ...snapshot, notifications: Array.isArray(snapshot.notifications) ? snapshot.notifications : [] };
});
export function subscribeOperations(onSnapshot: (snapshot: OperationsSnapshot) => void, onDisconnect: () => void) {
  const source = new EventSource(BASE + '/telemetry/stream');
  const receive = (event: MessageEvent<string>) => {
    try {
      const snapshot = JSON.parse(event.data) as OperationsSnapshot;
      if (!Number.isFinite(Date.parse(snapshot.serverTime)) || !Array.isArray(snapshot.positions) ||
          !Array.isArray(snapshot.simulations) || !Array.isArray(snapshot.trips) || !Array.isArray(snapshot.checkIns)) throw new Error('Invalid snapshot');
      onSnapshot({ ...snapshot, notifications: Array.isArray(snapshot.notifications) ? snapshot.notifications : [] });
    } catch { onDisconnect(); }
  };
  source.addEventListener('snapshot', receive);
  source.onerror = onDisconnect;
  return () => { source.removeEventListener('snapshot', receive); source.onerror = null; source.close(); };
}
export const controlSimulation = (tripId: number, action: SimulationAction, multiplier?: 1 | 5 | 10) =>
  request<SimulationRun>(`/trips/${tripId}/simulation/${action}`, {
    method: 'POST', ...(action === 'speed' ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ multiplier }) } : {}),
  });
