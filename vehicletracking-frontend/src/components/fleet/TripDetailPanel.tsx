import { useState } from 'react';
import { ArrowLeft, Check, MapPin, Play, RefreshCw, X } from 'lucide-react';
import type { TripAction, TripDetail } from '../../types/fleet';
import { TRIP_STATUS_LABELS } from '../../types/fleet';
import { displayTripTime } from '../../utils/tripTime';
import { FleetConfirmDialog } from './FleetConfirmDialog';

export function TripDetailPanel({ detail, loading, busy, error, onClose, onRetry, onAction, onFocusStop, onSimulate }: {
  detail: TripDetail | null; loading: boolean; busy: boolean; error: string | null;
  onClose: () => void; onRetry: () => void; onAction: (action: TripAction) => Promise<boolean>;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onSimulate?: (id: number) => void;
}) {
  const [confirm, setConfirm] = useState<'complete' | 'cancel' | null>(null);
  const trip = detail?.trip;
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
        <dl className="trip-times">
          <div><dt>Xuất phát kế hoạch</dt><dd>{displayTripTime(trip.scheduledDepartureAt)}</dd></div>
          <div><dt>Hoàn thành dự kiến</dt><dd>{displayTripTime(trip.plannedEndAt)}</dd></div>
          <div><dt>Khởi hành thực tế</dt><dd>{displayTripTime(trip.startedAt)}</dd></div>
          <div><dt>{trip.status === 'CANCELLED' ? 'Hủy lúc' : 'Kết thúc thực tế'}</dt><dd>{displayTripTime(trip.endedAt)}</dd></div>
        </dl>
        <p className="route-snapshot-note">Lịch kế hoạch của chuyến · Giờ địa phương. Lịch giữ nguyên khi xe khởi hành trễ; chưa có ETA theo traffic hoặc check-in tự động.</p>
        {onSimulate && <button className="fleet-text-button" disabled={busy} onClick={() => onSimulate(trip.id)}><Play size={14} />Mở mô phỏng chuyến này</button>}
        <ol className="trip-timeline">{detail.stops.map((stop,index) => <li key={stop.sequenceNumber}>
          <span className={`stop-order ${index === 0 ? 'start' : index === detail.stops.length - 1 ? 'end' : 'stop'}`}>{stop.sequenceNumber}</span>
          <div><button className="fleet-stop-link" onClick={() => onFocusStop([stop.latitude,stop.longitude],16)}><span>{stop.stationName}</span><MapPin size={13} /></button>
            <span className="fleet-help">{index === 0 ? 'Điểm đầu' : index === detail.stops.length - 1 ? 'Điểm cuối' : 'Trạm dừng'} · Bán kính {stop.checkinRadiusMeters} m</span>
            <div className="stop-schedule"><span>Đến <time dateTime={stop.plannedArrivalAt}>{displayTripTime(stop.plannedArrivalAt)}</time></span><span>Rời <time dateTime={stop.plannedDepartureAt}>{displayTripTime(stop.plannedDepartureAt)}</time></span></div>
            {stop.dwellDurationSeconds > 0 && <span className="fleet-help">Dừng {stop.dwellDurationSeconds} giây</span>}
          </div>
        </li>)}</ol>
      </>}
    </div>
    {trip && !loading && (trip.status === 'SCHEDULED' || trip.status === 'IN_PROGRESS') && <div className="fleet-footer">
      <button className="btn-secondary" disabled={busy} onClick={() => setConfirm('cancel')}><X size={14} />Hủy chuyến</button>
      {trip.status === 'SCHEDULED' ? <button className="btn-primary" disabled={busy} onClick={() => void onAction('start')}><Play size={14} />{busy ? 'Đang xử lý…' : 'Khởi hành'}</button>
        : <button className="btn-primary" disabled={busy} onClick={() => setConfirm('complete')}><Check size={14} />Hoàn thành</button>}
    </div>}
    {confirm && <FleetConfirmDialog title={confirm === 'cancel' ? 'Hủy chuyến đi?' : 'Hoàn thành chuyến đi?'}
      message={confirm === 'cancel' ? 'Chuyến sẽ kết thúc ở trạng thái đã hủy. Lịch sử được giữ lại.' : 'Xác nhận xe đã kết thúc chuyến. Thời điểm hiện tại sẽ được ghi nhận là giờ hoàn thành.'}
      confirmLabel={confirm === 'cancel' ? 'Xác nhận hủy chuyến' : 'Xác nhận hoàn thành'} busy={busy} error={error}
      onClose={() => setConfirm(null)} onConfirm={() => { void onAction(confirm).then(success => { if (success) setConfirm(null); }); }} />}
  </section>;
}
