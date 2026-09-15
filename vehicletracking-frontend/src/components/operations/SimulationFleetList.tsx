import type { useSimulationFleet } from '../../hooks/useSimulationFleet';
import type { OperationsSnapshot } from '../../types/operations';
import { SIMULATION_LABELS } from '../../types/operations';
import { simulationRouteColor } from '../../utils/simulationFleet';

export function SimulationFleetList({ fleet, snapshot, selectedTripId, disabled, onSelect, onFit, onManage }: {
  fleet: ReturnType<typeof useSimulationFleet>; snapshot: OperationsSnapshot | null; selectedTripId: number | null;
  disabled: boolean; onSelect: (tripId: number) => void; onFit: () => void; onManage: () => void;
}) {
  const running = fleet.trips.filter(trip => snapshot?.simulations.some(run => run.tripId === trip.id && run.status === 'RUNNING')).length;
  return <section className="simulation-fleet-list" aria-label="Đội xe mô phỏng">
    <div className="simulation-fleet-heading"><strong>{fleet.trips.length} xe · {running} đang chạy</strong>
      <button type="button" disabled={!fleet.trips.length} onClick={onFit}>Xem tất cả</button></div>
    <p className="simulation-route-legend">Tuyến xanh cyan: xe đang chọn · Các màu khác: xe còn lại.</p>
    {fleet.loading && <p role="status">Đang tải lộ trình đội xe…</p>}
    {fleet.routeFailures.length>0 && <div role="alert">
      <p>Chưa hiển thị tuyến: {fleet.routeFailures.map(item=>item.trip.vehiclePlateNumber).join(', ')}.</p>
      <button type="button" onClick={fleet.retry}>Thử tải lại tuyến</button>
    </div>}
    <details key={selectedTripId ?? 'all'} open={selectedTripId===null || undefined} className="simulation-fleet-picker">
    <summary>{selectedTripId===null ? 'Danh sách xe mô phỏng' : 'Chọn xe khác'}</summary>
    <p>Bấm xe trên bản đồ hoặc chọn bên dưới để điều khiển riêng. Các xe khác tiếp tục chạy.</p>
    <div className="simulation-fleet-rows">{fleet.trips.map(trip => {
      const run = snapshot?.simulations.find(item=>item.tripId===trip.id);
      const preview = fleet.previews.find(item=>item.trip.id===trip.id);
      return <button type="button" key={trip.id} data-simulation-trip={trip.id} disabled={disabled}
        aria-pressed={trip.id===selectedTripId} onClick={()=>onSelect(trip.id)}>
        <span><strong><i className="simulation-route-swatch" style={{background:simulationRouteColor(trip.vehicleId,trip.id===selectedTripId)}} aria-hidden="true" />{trip.vehiclePlateNumber}</strong><small>{run ? SIMULATION_LABELS[run.status] : 'Chờ xuất phát'}</small></span>
        <span>{preview ? `Trạm đầu: ${preview.start.stationName}` : trip.routeName}</span>
      </button>;
    })}</div>
    {!snapshot && <p role="status">Đang tải đội xe…</p>}
    {snapshot && !fleet.trips.length && <p>Chưa có xe chờ hoặc đang mô phỏng. Tạo xe và gán chuyến trước khi chạy.</p>}
    {fleet.loading && <p role="status">Đang tải vị trí trạm đầu…</p>}
    {fleet.failures.length > 0 && <div role="alert"><p>{fleet.failures.length} xe chưa tải được trạm đầu.</p>
      <button type="button" onClick={fleet.retry}>Thử lại vị trí chờ</button></div>}
    <button type="button" onClick={onManage}>Quản lý xe, tuyến & lịch khởi hành</button>
    </details>
  </section>;
}
