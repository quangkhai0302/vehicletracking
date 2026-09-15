import { useState } from 'react';
import { ArrowLeft, CalendarClock, Check, MapPin, Play, RefreshCw, Trash2, X } from 'lucide-react';
import type { TripAction, TripDetail } from '../../types/fleet';
import { TRIP_STATUS_LABELS } from '../../types/fleet';
import { displayTripTime, toLocalDateTimeInput } from '../../utils/tripTime';
import { FleetConfirmDialog } from './FleetConfirmDialog';
import { useTripCheckIns } from '../../hooks/useTripCheckIns';
import type { OperationsSnapshot } from '../../types/operations';
import type { StopVisit } from '../../types/checkin';
import { useTripEta } from '../../hooks/useTripEta';
import { trafficSourceLabel } from '../../utils/tripTraffic';
import { RouteRevisionPanel } from './RouteRevisionPanel';

export function TripDetailPanel({ detail, loading, busy, error, onClose, onRetry, onAction, onUpdateSchedule, onDeleteTrip, onFocusStop, onSimulate, liveSnapshot }: {
  detail: TripDetail | null; loading: boolean; busy: boolean; error: string | null;
  onClose: () => void; onRetry: () => void; onAction: (action: TripAction) => Promise<boolean>;
  onUpdateSchedule?: (id: number, input: { scheduledDepartureAt: string }) => Promise<boolean>;
  onDeleteTrip?: (id: number) => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onSimulate?: (id: number) => void; liveSnapshot?: OperationsSnapshot | null;
}) {
  const [confirm, setConfirm] = useState<'complete' | 'cancel' | null>(null);
  const [editingSchedule, setEditingSchedule] = useState(false);
  const [schedule, setSchedule] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const trip = detail?.trip;
  const checkins = useTripCheckIns(trip?.id ?? null, liveSnapshot);
  const eta = useTripEta(trip?.id ?? null);
  const visitByStop = new Map(checkins.data?.visits.map(visit => [visit.stopSequence, visit]) ?? []);
  const etaByStop = new Map(eta.data?.stops.map(stop => [stop.sequenceNumber, stop]) ?? []);
  const beginScheduleEdit = () => { if (trip) { setSchedule(toLocalDateTimeInput(new Date(trip.scheduledDepartureAt))); setEditingSchedule(true); } };
  const saveSchedule = async () => { if (!trip || !onUpdateSchedule) return; const date = new Date(schedule); if (!Number.isFinite(date.getTime())) return; const ok = await onUpdateSchedule(trip.id, { scheduledDepartureAt: date.toISOString() }); if (ok) setEditingSchedule(false); };
  return <section className="fleet-editor" aria-label="Chi tiết chuyến đi">
    <div className="fleet-heading"><button className="fleet-icon-button" disabled={busy} aria-label="Đóng chi tiết chuyến" onClick={onClose}><ArrowLeft size={18} /></button>
      <div><span className="panel-eyebrow">LỊCH TRÌNH CHUYẾN ĐI</span><h2>{trip ? `Chuyến #${trip.id}` : 'Chi tiết chuyến'}</h2></div>
      <button className="fleet-icon-button" disabled={busy || loading} aria-label="Tải lại chuyến" onClick={onRetry}><RefreshCw size={16} /></button></div>
    <div className="fleet-detail-body">
      {loading && <p role="status" className="fleet-loading">Đang tải lịch trình…</p>}
      {error && !confirm && <div className="fleet-error" role="alert">{error}<button className="fleet-text-button" disabled={busy || loading} onClick={onRetry}>Tải lại trạng thái chuyến</button></div>}
      {!loading && trip && detail && <>
        <div className="trip-summary"><span className={`trip-status ${trip.status.toLowerCase()}`}>{TRIP_STATUS_LABELS[trip.status]}</span>
          <h3>{trip.vehiclePlateNumber}</h3><p>{trip.routeName}</p></div>
        <details className="trip-traffic-details"><summary>Lịch trình dự kiến và thực tế</summary>
        <dl className="trip-times">
          <div><dt>Xuất phát kế hoạch</dt><dd>{displayTripTime(trip.scheduledDepartureAt)}</dd></div>
          <div><dt>Hoàn thành theo lịch</dt><dd>{displayTripTime(trip.plannedEndAt)}</dd></div>
          <div><dt>Khởi hành thực tế</dt><dd>{displayTripTime(trip.startedAt)}</dd></div>
          <div><dt>{trip.status === 'CANCELLED' ? 'Hủy lúc' : 'Kết thúc thực tế'}</dt><dd>{displayTripTime(trip.endedAt)}</dd></div>
        </dl>
        </details>
        {editingSchedule ? <div className="trip-schedule-editor"><label>Giờ xuất phát mới<input type="datetime-local" value={schedule} onChange={event => setSchedule(event.target.value)} min="2000-01-01T00:00" max="2100-12-31T23:59" /></label><div><button className="btn-secondary" disabled={busy} onClick={() => setEditingSchedule(false)}>Hủy</button><button className="btn-primary" disabled={busy || !schedule} onClick={() => void saveSchedule()}><CalendarClock size={14} />Lưu giờ xuất phát</button></div></div> : trip.status === 'SCHEDULED' && onUpdateSchedule && <button className="fleet-text-button" disabled={busy} onClick={beginScheduleEdit}><CalendarClock size={14} />Sửa giờ xuất phát</button>}
        <div className="trip-checkin-summary" aria-live="polite">
          {checkins.loading ? 'Đang tải ghi nhận check-in…' : checkins.error ? <>{checkins.error} <button className="fleet-text-button" onClick={checkins.retry}>Tải lại</button></> :
            checkins.data?.revision === 0 ? 'Chưa có dữ liệu check-in cho chuyến này.' : `Đã ghi nhận ${checkins.data?.visits.length ?? 0}/${detail.stops.length} điểm dừng.`}
        </div>
        <div className="trip-eta-summary" aria-live="polite">
          {eta.loading && !eta.data ? 'Đang tính ETA theo giao thông…' : eta.error ? <>{eta.error} <button className="fleet-text-button" onClick={eta.retry}>Thử lại ETA</button></> : eta.data ? <>
            <strong>{eta.data.status === 'BLOCKED' ? 'Đường phía trước bị chặn, chưa xác định giờ đến.' : `Còn khoảng ${Math.ceil(eta.data.totalRemainingSeconds / 60)} phút đến cuối tuyến`}</strong>
            {(eta.data.source !== 'HERE_LIVE' || eta.data.status === 'STALE') && <small>{eta.data.status === 'STALE' ? 'Ước tính theo dữ liệu giao thông gần nhất' : trafficSourceLabel[eta.data.source]}</small>}
          </> : 'Chưa có ETA traffic; đang dùng lịch tuyến đã lưu.'}
        </div>
        {onSimulate && <button className="fleet-text-button" disabled={busy} onClick={() => onSimulate(trip.id)}><Play size={14} />Mở mô phỏng chuyến này</button>}
        <ol className="trip-timeline">{detail.stops.map((stop,index) => <li key={stop.sequenceNumber}>
          <span className={`stop-order ${index === 0 ? 'start' : index === detail.stops.length - 1 ? 'end' : 'stop'}`}>{stop.sequenceNumber}</span>
          <div><button className="fleet-stop-link" onClick={() => onFocusStop([stop.latitude,stop.longitude],16)}><span>{stop.stationName}</span><MapPin size={13} /></button>
            <span className="fleet-help">{index === 0 ? 'Điểm đầu' : index === detail.stops.length - 1 ? 'Điểm cuối' : 'Trạm dừng'}</span>
            {!visitByStop.has(stop.sequenceNumber) && <span className="trip-eta-stop">Dự kiến đến: {etaByStop.get(stop.sequenceNumber)?.etaAt ? displayTripTime(etaByStop.get(stop.sequenceNumber)!.etaAt) : eta.data?.status === 'BLOCKED' ? 'Đường bị đóng' : `${displayTripTime(stop.plannedArrivalAt)} (theo lịch)`}</span>}
            <VisitStatus visit={visitByStop.get(stop.sequenceNumber)}
              awaitingExit={checkins.data?.awaitingExit === true && checkins.data.nextStopSequence === stop.sequenceNumber} />
          </div>
        </li>)}</ol>
        <RouteRevisionPanel tripId={trip.id} />
      </>}
    </div>
    {trip && !loading && (trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS') && <div className="fleet-footer">
      <button className="btn-secondary" disabled={busy} onClick={() => setConfirm('cancel')}><X size={14} />Hủy chuyến</button>
      {trip.status === 'SCHEDULED' ? <button className="btn-primary" disabled={busy} onClick={() => void onAction('start')}><Play size={14} />{busy ? 'Đang xử lý…' : 'Khởi hành'}</button>
        : <button className="btn-primary" disabled={busy} onClick={() => setConfirm('complete')}><Check size={14} />Hoàn thành</button>}
      {trip.status === 'SCHEDULED' && onDeleteTrip && <button className="danger-action" disabled={busy} onClick={() => setConfirmDelete(true)}><Trash2 size={14} />Xóa chuyến</button>}
    </div>}
    {confirm && <FleetConfirmDialog title={confirm === 'cancel' ? 'Hủy chuyến đi?' : 'Hoàn thành chuyến đi?'}
      message={confirm === 'cancel' ? 'Chuyến sẽ kết thúc ở trạng thái đã hủy. Lịch sử được giữ lại.' : 'Xác nhận xe đã kết thúc chuyến. Thời điểm hiện tại sẽ được ghi nhận là giờ hoàn thành.'}
      confirmLabel={confirm === 'cancel' ? 'Xác nhận hủy chuyến' : 'Xác nhận hoàn thành'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => { void onAction(confirm).then(success => { if (success) setConfirm(null); }); }} />}
    {confirmDelete && trip && onDeleteTrip && <FleetConfirmDialog title="Xóa chuyến chưa khởi hành?" message="Chỉ chuyến SCHEDULED chưa có dữ liệu vận hành mới xóa được. Hành động này không thể hoàn tác." confirmLabel="Xác nhận xóa chuyến" busy={busy} error={error} onClose={() => setConfirmDelete(false)} onConfirm={() => { void onDeleteTrip(trip.id).then(success => { if (success) setConfirmDelete(false); }); }} />}
  </section>;
}

function VisitStatus({ visit, awaitingExit }: { visit?: StopVisit; awaitingExit?: boolean }) {
  if (!visit) return <span className="trip-checkin-pending">{awaitingExit ? 'Chờ ra khỏi vùng rồi vào lại' : 'Chưa ghi nhận'}</span>;
  const time = visit.source === 'SIMULATOR' && visit.simulatedArrivalAt ? visit.simulatedArrivalAt : visit.actualArrivalAt;
  return <span className="trip-checkin-done">Đã qua trạm lúc {displayTripTime(time)}{visit.source === 'SIMULATOR' ? ' · Mô phỏng' : ''}</span>;
}
