import { useEffect, useState } from 'react';
import { BusFront, CalendarDays, Edit3, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';
import type { FleetVehicle, TripStatus } from '../../types/fleet';
import { TRIP_STATUS_LABELS } from '../../types/fleet';
import type { RouteDetail } from '../../types/route';
import { useFleetWorkspace } from '../../hooks/useFleetWorkspace';
import { displayTripTime } from '../../utils/tripTime';
import { VehicleEditor } from './VehicleEditor';
import { TripEditor } from './TripEditor';
import { TripDetailPanel } from './TripDetailPanel';
import { FleetConfirmDialog } from './FleetConfirmDialog';
import './fleet.css';
import type { OperationsSnapshot } from '../../types/operations';

export function FleetWorkspace({ onToast, onTripRoute, onFocusStop, onManageRoutes, onManageStations, liveSnapshot, onSimulateTrip, onFocusVehicle }: {
  onToast: (message: string) => void;
  onTripRoute: (route: RouteDetail | null | undefined) => void;
  onFocusStop: (position: [number, number], zoom?: number) => void;
  onManageRoutes: () => void; onManageStations: () => void;
  liveSnapshot?: OperationsSnapshot | null; onSimulateTrip?: (id: number) => void; onFocusVehicle?: (id: number) => void;
}) {
  const fleet = useFleetWorkspace(onToast, liveSnapshot);
  const [query, setQuery] = useState('');
  const [vehicleStatus, setVehicleStatus] = useState<'active' | 'inactive' | 'all'>('active');
  const [tripStatus, setTripStatus] = useState<TripStatus | ''>('');
  const [deactivate, setDeactivate] = useState<FleetVehicle | null>(null);
  const { screen, detail } = fleet;
  useEffect(() => {
    onTripRoute(screen.kind === 'trip-detail' ? detail?.route ?? null : undefined);
  }, [screen.kind, detail?.route, onTripRoute]);
  const q = query.trim().toLocaleLowerCase('vi');
  const vehicles = fleet.vehicles.filter(vehicle => (vehicleStatus === 'all' || vehicle.active === (vehicleStatus === 'active')) &&
    (!q || `${vehicle.plateNumber} ${vehicle.name}`.toLocaleLowerCase('vi').includes(q)));
  const trips = fleet.trips.filter(trip => (!fleet.vehicleFilter || trip.vehicleId === fleet.vehicleFilter) && (!tripStatus || trip.status === tripStatus) &&
    (!q || `${trip.id} ${trip.vehiclePlateNumber} ${trip.routeName}`.toLocaleLowerCase('vi').includes(q)));
  const filteredVehicle = fleet.vehicles.find(vehicle => vehicle.id === fleet.vehicleFilter);

  return <div className="fleet-workspace">
    <div className="fleet-list-view" hidden={screen.kind !== 'list'}>
      <div className="planning-tabs fleet-tabs" aria-label="Quản lý đội xe">
        <button aria-pressed={fleet.tab === 'vehicles'} onClick={() => { fleet.setTab('vehicles'); setQuery(''); }}><BusFront size={14} />Đội xe <span>{fleet.vehicles.filter(vehicle => vehicle.active).length}</span></button>
        <button aria-pressed={fleet.tab === 'trips'} onClick={() => { fleet.setTab('trips'); setQuery(''); }}><CalendarDays size={14} />Chuyến đi <span>{fleet.trips.length}</span></button>
      </div>
      <div className="fleet-heading"><div><span className="panel-eyebrow">DỮ LIỆU VẬN HÀNH</span><h2>{fleet.tab === 'vehicles' ? 'Danh sách xe' : 'Lịch chuyến đi'}</h2></div>
        <button className="fleet-icon-button" disabled={fleet.loading || fleet.busy} aria-label="Tải lại đội xe và chuyến" onClick={() => void fleet.reload()}><RefreshCw size={16} /></button></div>
      <div className="fleet-list-tools">
        <label className="fleet-search"><Search size={15} /><input aria-label={fleet.tab === 'vehicles' ? 'Tìm xe' : 'Tìm chuyến đi'} placeholder={fleet.tab === 'vehicles' ? 'Biển số hoặc tên xe…' : 'Biển số, tuyến hoặc mã chuyến…'} value={query} onChange={e => setQuery(e.target.value)} /></label>
        {fleet.tab === 'vehicles' ? <select aria-label="Lọc xe" value={vehicleStatus} onChange={e => setVehicleStatus(e.target.value as typeof vehicleStatus)}>
          <option value="active">Đang sử dụng</option><option value="inactive">Đã ngừng sử dụng</option><option value="all">Tất cả xe</option>
        </select> : <>
          <select aria-label="Lọc trạng thái chuyến" value={tripStatus} onChange={e => setTripStatus(e.target.value as TripStatus | '')}>
            <option value="">Mọi trạng thái</option>{Object.entries(TRIP_STATUS_LABELS).map(([id,label]) => <option key={id} value={id}>{label}</option>)}
          </select>
          {fleet.vehicleFilter && <div className="fleet-filter-chip"><span>Xe {filteredVehicle?.plateNumber ?? fleet.vehicleFilter}</span><button onClick={() => fleet.setVehicleFilter(null)}>Bỏ lọc xe</button></div>}
        </>}
      </div>
      <div className="fleet-list-body" aria-busy={fleet.loading}>
        {fleet.loading && <p className="fleet-loading" role="status">Đang tải xe và chuyến đi…</p>}
        {fleet.error && !deactivate && <div className="fleet-error" role="alert">{fleet.error}<button className="fleet-text-button" onClick={() => void fleet.reload()}>Thử lại</button></div>}
        {!fleet.loading && !fleet.error && fleet.tab === 'vehicles' && <>
          {vehicles.length === 0 && <div className="fleet-empty"><BusFront size={30} /><h3>{fleet.vehicles.length ? 'Không tìm thấy xe phù hợp' : 'Chưa có xe trong danh mục'}</h3><p>Thêm xe, sau đó tạo chuyến từ tuyến đã lưu.</p><button className="fleet-text-button" onClick={onManageStations}>Thiết lập trạm</button></div>}
          {vehicles.map(vehicle => <article key={vehicle.id} className="fleet-vehicle-card">
            <div className="fleet-card-main"><button className="fleet-vehicle-select" onClick={() => { setQuery(''); fleet.showVehicleTrips(vehicle.id); }}>
              <span className="fleet-plate"><BusFront size={16} />{vehicle.plateNumber}</span><strong>{vehicle.name}</strong>
              <span className="fleet-help">{vehicle.active ? 'Đang sử dụng' : 'Đã ngừng sử dụng'} · {liveSnapshot?.positions.some(point => point.vehicleId === vehicle.id) ? 'Đã nhận vị trí' : 'Chưa có vị trí xe'}</span>
            </button>
            {vehicle.active && <div className="fleet-card-actions"><button className="fleet-icon-button" aria-label={`Sửa xe ${vehicle.plateNumber}`} onClick={() => fleet.openVehicleForm(vehicle)}><Edit3 size={15} /></button>
              <button className="fleet-icon-button danger" aria-label={`Ngừng sử dụng xe ${vehicle.plateNumber}`} onClick={() => setDeactivate(vehicle)}><Trash2 size={15} /></button></div>}</div>
            {vehicle.description && <p className="fleet-card-description">{vehicle.description}</p>}
            {liveSnapshot?.positions.some(point => point.vehicleId === vehicle.id) && <button className="fleet-text-button" onClick={() => onFocusVehicle?.(vehicle.id)}>Xem vị trí xe</button>}
            <button className="fleet-text-button" onClick={() => { setQuery(''); fleet.showVehicleTrips(vehicle.id); }}>Xem chuyến đi <span>→</span></button>
          </article>)}
        </>}
        {!fleet.loading && !fleet.error && fleet.tab === 'trips' && <>
          {trips.length === 0 && <div className="fleet-empty"><CalendarDays size={30} /><h3>Chưa có chuyến phù hợp</h3><p>Chọn xe, tuyến và giờ xuất phát để lập lịch trình.</p></div>}
          {trips.map(trip => <button key={trip.id} className="fleet-trip-card" onClick={() => void fleet.selectTrip(trip.id)}>
            <span className="fleet-trip-title"><strong>#{trip.id} · {trip.vehiclePlateNumber}</strong><span className={`trip-status ${trip.status.toLowerCase()}`}>{TRIP_STATUS_LABELS[trip.status]}</span></span>
            <span className="fleet-trip-route">{trip.routeName}</span><span className="fleet-help">Xuất phát {displayTripTime(trip.scheduledDepartureAt)}</span>
            <span className="fleet-help">Dự kiến đến {displayTripTime(trip.plannedEndAt)}</span>
          </button>)}
        </>}
      </div>
      <div className="fleet-footer">
        {fleet.tab === 'vehicles' ? <button className="btn-primary" disabled={fleet.loading || !!fleet.error} onClick={() => fleet.openVehicleForm(null)}><Plus size={16} />Thêm xe mới</button>
          : <button className="btn-primary" disabled={fleet.loading || !!fleet.error || !fleet.vehicles.some(vehicle => vehicle.active)} onClick={fleet.openTripForm}><Plus size={16} />Tạo chuyến mới</button>}
      </div>
      {fleet.tab === 'trips' && !fleet.loading && !fleet.vehicles.some(vehicle => vehicle.active) && <p className="fleet-prerequisite">Thêm ít nhất một xe đang sử dụng ở tab Đội xe.</p>}
    </div>
    {screen.kind === 'vehicle-form' && <VehicleEditor key={screen.vehicle?.id ?? 'new'} vehicle={screen.vehicle} busy={fleet.busy} error={fleet.error}
      onSave={async (input, id) => { const saved = await fleet.saveVehicle(input, id); if (saved) { setQuery(''); setVehicleStatus('active'); } return saved; }} onClose={fleet.close} />}
    {screen.kind === 'trip-form' && <TripEditor vehicles={fleet.vehicles} initialVehicleId={screen.vehicleId} busy={fleet.busy} error={fleet.error} onSave={fleet.saveTrip} onClose={fleet.close} onManageRoutes={onManageRoutes} />}
    {screen.kind === 'trip-detail' && <TripDetailPanel key={screen.id} detail={fleet.detail} loading={fleet.loadingDetail} busy={fleet.busy} error={fleet.error} onClose={fleet.close}
      onRetry={() => void fleet.selectTrip(screen.id)} onAction={fleet.transition} onUpdateSchedule={fleet.updateTripSchedule} onDeleteTrip={fleet.removeTrip} onFocusStop={onFocusStop} onSimulate={onSimulateTrip} liveSnapshot={liveSnapshot} />}
    {deactivate && <FleetConfirmDialog title={`Ngừng sử dụng xe ${deactivate.plateNumber}?`} message="Các chuyến chưa kết thúc phải được hoàn thành hoặc hủy trước. Lịch sử xe và chuyến đi vẫn được lưu."
      confirmLabel="Xác nhận ngừng sử dụng xe" busy={fleet.busy} error={fleet.error} onClose={() => setDeactivate(null)}
      onConfirm={() => { void fleet.removeVehicle(deactivate).then(success => { if (success) setDeactivate(null); }); }} />}
  </div>;
}
