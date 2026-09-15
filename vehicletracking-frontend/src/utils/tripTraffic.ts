import type { TripEta } from '../types/eta';
import type { SimulationRun } from '../types/operations';
import type { TrafficSource } from '../types/traffic';

export const trafficSourceLabel: Record<TrafficSource, string> = {
  HERE_LIVE: 'Ước tính theo giao thông', HERE_LAST_KNOWN: 'Ước tính theo dữ liệu giao thông gần nhất',
  GOOGLE_LIVE: 'ETA giao thông trực tiếp từ Google', GOOGLE_LAST_KNOWN: 'ETA Google gần nhất đã lưu',
  ROUTE_SNAPSHOT: 'Ước tính theo tuyến đã lưu', UNAVAILABLE: 'Chưa có dữ liệu giao thông',
};
const seconds = (value: number | null | undefined) => value != null && Number.isFinite(value) && value >= 0 ? Math.ceil(value) : null;
export const remainingTime = (value: number | null) => value === null ? 'Chưa xác định' : value < 60 ? `${value} giây` : `${Math.ceil(value / 60)} phút`;

// Keep the ETA value, stop and source from the same response. A newer simulation
// response can supersede the 10-second ETA poll, including a closure or recovery.
export function tripTrafficView(eta: TripEta | null, run: SimulationRun | null) {
  const metadata = run?.traffic;
  const usableMetadata = !!metadata && metadata.source !== 'UNAVAILABLE' && metadata.status !== 'UNAVAILABLE';
  const useRun = usableMetadata && (!eta || Date.parse(run!.updatedAt) > Date.parse(eta.calculatedAt));
  const context = useRun ? metadata : eta;
  const blocked = context?.status === 'BLOCKED' || (useRun && metadata?.blocked === true);
  const finished = run?.frame?.finished === true || run?.status === 'COMPLETED' || run?.status === 'STOPPED'
    || (!useRun && eta !== null && eta.nextStopSequence === null);
  const nextStopSequence = finished ? null : useRun ? run?.frame?.nextStopSequence ?? eta?.nextStopSequence ?? null
    : eta?.nextStopSequence ?? run?.frame?.nextStopSequence ?? null;
  const stop = eta?.stops.find(item => item.sequenceNumber === nextStopSequence && item.state !== 'CHECKED_IN');
  let countdown = finished || blocked ? null : seconds(useRun ? metadata?.nextStopEtaSeconds : stop?.etaSeconds);
  let source = context?.source ?? 'UNAVAILABLE';
  if (countdown === null && !blocked && !finished && run?.frame && nextStopSequence === run.frame.nextStopSequence) {
    countdown = seconds(run.frame.nextStopEtaSeconds);
    source = 'ROUTE_SNAPSHOT';
  }
  const impacts = new Set<string>();
  for (const segment of eta?.affectedSegments ?? []) {
    if (finished || (nextStopSequence !== null && segment.destinationStopSequence < nextStopSequence)) continue;
    if (segment.kind === 'FLOW' && segment.jamFactor >= 4) impacts.add(segment.jamFactor >= 8 ? 'Ùn tắc nghiêm trọng' : 'Dòng xe di chuyển chậm');
    if (segment.kind === 'INCIDENT') {
      const type = segment.traversability.toLowerCase();
      impacts.add(type.includes('accident') || type.includes('collision') ? 'Tai nạn'
        : type.includes('construction') || type.includes('work') ? 'Công trường / thi công'
          : type.includes('closure') || type.includes('blocked') ? 'Đóng đường' : 'Sự cố giao thông');
    }
  }
  const delay = eta && !blocked && !finished && eta.source !== 'ROUTE_SNAPSHOT' && eta.source !== 'UNAVAILABLE'
    ? Math.max(0, eta.totalRemainingSeconds - eta.baselineRemainingSeconds) : null;
  return { blocked, finished, nextStopSequence, stationName: stop?.stationName, countdown, source,
    status: context?.status ?? 'UNAVAILABLE', impacts: [...impacts], delay,
    observedAt: useRun ? metadata?.observedAt : eta?.trafficObservedAt,
    fetchedAt: useRun ? metadata?.fetchedAt : eta?.trafficFetchedAt,
    etaAt: !blocked && !finished && !useRun ? stop?.etaAt : null,
    warning: context?.warning };
}
