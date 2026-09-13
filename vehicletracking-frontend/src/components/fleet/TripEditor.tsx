import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, CalendarPlus, RefreshCw } from 'lucide-react';
import type { FleetVehicle, TripInput } from '../../types/fleet';
import type { RouteSummary } from '../../types/route';
import { fetchRoutes } from '../../services/routes';
import { displayTripTime, toLocalDateTimeInput } from '../../utils/tripTime';
import { FleetConfirmDialog } from './FleetConfirmDialog';

export function TripEditor({ vehicles, initialVehicleId, busy, error, onSave, onClose, onManageRoutes }: {
  vehicles: FleetVehicle[]; initialVehicleId: number | null; busy: boolean; error: string | null;
  onSave: (input: TripInput) => Promise<boolean>; onClose: () => void; onManageRoutes: () => void;
}) {
  const [vehicleId, setVehicleId] = useState(initialVehicleId ? String(initialVehicleId) : '');
  const [routeId, setRouteId] = useState('');
  const [departure, setDeparture] = useState(() => toLocalDateTimeInput(new Date(Date.now() + 15 * 60000)));
  const [initialDeparture] = useState(departure);
  const [routes, setRoutes] = useState<RouteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [routeError, setRouteError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [confirm, setConfirm] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    fetchRoutes(controller.signal).then(data => {
      if (!controller.signal.aborted) { setRoutes(data); setRouteError(null); }
    }).catch((err: unknown) => {
      if (!controller.signal.aborted) setRouteError(err instanceof Error ? err.message : 'Không thể tải tuyến.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt]);
  const retry = () => { setLoading(true); setRouteError(null); setAttempt(value => value + 1); };
  const selectedRoute = routes.find(route => route.id === Number(routeId));
  const date = new Date(departure);
  const validTime = Number.isFinite(date.getTime()) && date.getTime() >= Date.UTC(2000,0,1) && date.getTime() < Date.UTC(2101,0,1);
  const valid = vehicles.some(vehicle => vehicle.id === Number(vehicleId) && vehicle.active) && selectedRoute && validTime && !loading && !routeError;
  const dirty = routeId !== '' || vehicleId !== (initialVehicleId ? String(initialVehicleId) : '') || departure !== initialDeparture;
  const close = () => { if (dirty) setConfirm(true); else onClose(); };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (busy || !valid) return;
    void onSave({ vehicleId: Number(vehicleId), routeId: Number(routeId), scheduledDepartureAt: date.toISOString() });
  };
  return <section className="fleet-editor" aria-label="Tạo chuyến đi">
    <div className="fleet-heading"><button className="fleet-icon-button" aria-label="Đóng biểu mẫu chuyến" onClick={close} disabled={busy}><ArrowLeft size={18} /></button>
      <div><span className="panel-eyebrow">LẬP LỊCH VẬN HÀNH</span><h2>Tạo chuyến đi</h2></div></div>
    <form id="trip-form" className="fleet-form-body" onSubmit={submit}><fieldset disabled={busy}>
      {error && <p className="fleet-error" role="alert">{error}</p>}
      <label>Xe thực hiện *<select aria-label="Xe thực hiện *" value={vehicleId} required onChange={e => setVehicleId(e.target.value)}>
        <option value="">Chọn xe</option>{vehicles.filter(vehicle => vehicle.active).map(vehicle => <option key={vehicle.id} value={vehicle.id}>{vehicle.plateNumber} · {vehicle.name}</option>)}
      </select></label>
      <label>Tuyến đường *<select aria-label="Tuyến đường *" value={routeId} required onChange={e => setRouteId(e.target.value)} disabled={loading || !!routeError}>
        <option value="">{loading ? 'Đang tải tuyến…' : 'Chọn tuyến đã lưu'}</option>
        {routes.map(route => <option key={route.id} value={route.id}>{route.name}</option>)}
      </select></label>
      {routeError && <p className="fleet-error" role="alert">{routeError}</p>}
      <div className="fleet-inline-actions"><button type="button" className="fleet-text-button" disabled={loading} onClick={retry}><RefreshCw size={13} />Tải lại tuyến</button><button type="button" className="fleet-text-button" onClick={onManageRoutes}>Quản lý tuyến</button></div>
      {!loading && !routeError && routes.length === 0 && <p className="fleet-help">Chưa có tuyến. Tạo tuyến ở mục Tuyến & trạm, sau đó quay lại và tải lại danh sách.</p>}
      <label>Giờ xuất phát *<input type="datetime-local" value={departure} required min="2000-01-01T00:00" max="2100-12-31T23:59" onChange={e => setDeparture(e.target.value)} /></label>
      <p className="fleet-help">Giờ địa phương: {Intl.DateTimeFormat().resolvedOptions().timeZone}. Lịch kế hoạch được giữ nguyên khi xe khởi hành trễ.</p>
      {validTime && selectedRoute && <div className="trip-preview"><span>Dự kiến hoàn thành</span><strong>{displayTripTime(new Date(date.getTime() + selectedRoute.estimatedTripDurationSeconds * 1000).toISOString())}</strong><span>{selectedRoute.stopCount} điểm · {(selectedRoute.totalDistanceMeters / 1000).toFixed(1)} km</span></div>}
      <p className="route-snapshot-note">Thời gian dựa trên tuyến đã tính lúc {selectedRoute ? displayTripTime(selectedRoute.calculatedAt) : 'tạo tuyến'}. Chưa phải ETA theo giao thông hiện tại.</p>
    </fieldset></form>
    <div className="fleet-footer"><button className="btn-secondary" disabled={busy} onClick={close}>Hủy</button><button className="btn-primary" type="submit" form="trip-form" disabled={busy || !valid}><CalendarPlus size={15} />{busy ? 'Đang tạo…' : 'Tạo chuyến'}</button></div>
    {confirm && <FleetConfirmDialog title="Bỏ bản nháp chuyến đi?" message="Xe, tuyến và giờ xuất phát chưa lưu sẽ bị xóa." confirmLabel="Bỏ bản nháp" busy={false} onConfirm={onClose} onClose={() => setConfirm(false)} />}
  </section>;
}
