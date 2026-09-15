import { Gauge, MapPin } from 'lucide-react';
import { useTripEta } from '../../hooks/useTripEta';
import type { TripStop } from '../../types/fleet';
import type { SimulationRun, StreamConnection, TelemetryPosition } from '../../types/operations';
import { positionFreshness } from '../../types/operations';
import { remainingTime, trafficSourceLabel, tripTrafficView } from '../../utils/tripTraffic';
import { displayTripTime } from '../../utils/tripTime';

export function TripTrafficSummary({ tripId, run, stops = [], position, now, connection, context = 'simulation' }: {
  tripId: number; run: SimulationRun | null; stops?: TripStop[]; position?: TelemetryPosition | null;
  now: number; connection: StreamConnection; context?: 'simulation' | 'vehicle';
}) {
  const eta = useTripEta(tripId);
  const etaMatchesRun = !eta.data || !run
    || ((run.attemptNumber === undefined || eta.data.attemptNumber === run.attemptNumber)
      && (run.routeRevisionId === undefined || eta.data.routeRevisionId === run.routeRevisionId));
  const view = tripTrafficView(etaMatchesRun ? eta.data : null, run);
  const frame = run?.frame;
  const sample = context === 'simulation' ? frame : position;
  const speed = sample && Number.isFinite(sample.speedKmh) ? sample.speedKmh : null;
  const sampleAt = context === 'simulation' ? run?.updatedAt : position?.recordedAt;
  const oldPosition = context === 'simulation' ? !!sampleAt && now - Date.parse(sampleAt) > 15_000
    : !!position && positionFreshness(position, now) !== 'fresh';
  const liveTraffic = view.source === 'HERE_LIVE' || view.source === 'GOOGLE_LIVE';
  const staleTraffic = view.status === 'STALE' || view.source === 'HERE_LAST_KNOWN' || view.source === 'GOOGLE_LAST_KNOWN'
    || !!(view.observedAt ?? view.fetchedAt) && now - Date.parse((view.observedAt ?? view.fetchedAt)!) > 90_000 || !!eta.error;
  const stopName = view.stationName ?? stops.find(stop => stop.sequenceNumber === view.nextStopSequence)?.stationName
    ?? (view.nextStopSequence !== null ? `Trạm ${view.nextStopSequence}` : 'Chưa xác định trạm tiếp theo');
  const sourceText = staleTraffic && liveTraffic ? 'Ước tính theo dữ liệu giao thông gần nhất' : trafficSourceLabel[view.source];
  return <section className="trip-traffic-card" aria-label={context === 'simulation' ? 'Vị trí, vận tốc và ETA mô phỏng' : 'Vị trí, vận tốc và ETA xe'}>
    <div className="telemetry-grid">
      <div><span><Gauge size={13} /> {oldPosition || connection !== 'live' ? 'Vận tốc gần nhất' : 'Vận tốc xe'}</span>
        <strong data-testid={`${context}-speed`}>{speed === null ? '—' : speed.toFixed(1)} <small>km/h</small></strong></div>
      <div><span>Còn khoảng tới trạm</span><strong data-testid={`${context}-eta`}>
        {view.finished ? 'Đã kết thúc' : view.blocked ? 'Đường bị chặn' : remainingTime(view.countdown)}</strong></div>
    </div>
    <div className="trip-traffic-next"><MapPin size={14} /><strong>{view.finished ? 'Chuyến đã kết thúc' : stopName}</strong></div>
    {view.etaAt && run?.status !== 'PAUSED' && <p>Dự kiến đến: {displayTripTime(view.etaAt)}</p>}
    {!sample && <p>Chưa có vị trí xe; bắt đầu chuyến để theo dõi.</p>}
    {(staleTraffic || !liveTraffic || view.source === 'GOOGLE_LIVE' || !etaMatchesRun || (eta.loading && !eta.data)) && <p className="trip-traffic-source" data-stale={staleTraffic || view.source === 'ROUTE_SNAPSHOT'}>{!etaMatchesRun ? 'Đang đồng bộ ETA với lần chạy và tuyến mới…' : eta.loading && !eta.data && !run?.traffic ? 'Đang tính giờ đến…' : sourceText}</p>}
    {view.blocked && <p className="trip-traffic-blocked" role="status">Đường phía trước bị chặn. Chưa thể xác định thời gian đến trạm.</p>}
    {view.impacts.length > 0 && <p className="trip-traffic-impacts">Ảnh hưởng trên phần tuyến còn lại: {view.impacts.join(' · ')}.</p>}
    {view.delay !== null && view.delay >= 60 && <p>Phần tuyến còn lại chậm hơn khoảng {remainingTime(Math.ceil(view.delay))} so với tuyến đã lưu.</p>}
    {view.warning && !view.blocked && !view.finished && <p>{view.warning.startsWith('VEHICLE_POSITION_UNAVAILABLE')
      ? 'Chưa xác định được vị trí xe trên tuyến; ETA dựa trên lịch đã lưu.'
      : 'Giao thông chưa đủ dữ liệu; một phần ETA có thể dựa trên tuyến đã lưu.'}</p>}
    {eta.error && !view.finished && <p className="trip-traffic-impacts" role="status">Chưa làm mới được ETA. <button type="button" onClick={eta.retry}>Thử lại</button></p>}
    {(oldPosition || connection !== 'live') && !view.finished && <p>Vị trí chưa được cập nhật trực tiếp.</p>}
    {run?.status === 'PAUSED' && <p>Mô phỏng đang tạm dừng; thời gian tới trạm áp dụng khi tiếp tục chạy.</p>}
    <details className="trip-traffic-details"><summary>Thông tin kỹ thuật</summary>
      {sample && <p className="trip-traffic-position">Vị trí: {sample.latitude.toFixed(5)}, {sample.longitude.toFixed(5)} · {context === 'simulation' || position?.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS'}</p>}
      {sampleAt && <p className="trip-traffic-update">Vị trí cập nhật: {displayTripTime(sampleAt)}</p>}
      {view.fetchedAt && <p className="trip-traffic-update">Giao thông cập nhật: {displayTripTime(view.observedAt ?? view.fetchedAt)} · ETA làm mới mỗi 10 giây.</p>}
    </details>
  </section>;
}
