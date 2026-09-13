import { useMemo, useState } from 'react';
import {
  AlertTriangle,
  BusFront,
  Clock3,
  Gauge,
  MapPin,
  MapPinned,
  Radio,
  Route,
  Search,
} from 'lucide-react';
import type { Vehicle } from '../types/vehicle';

export type VehicleFilterTab = 'ALL' | 'RUNNING' | 'DELAYED';

interface TrackingPanelProps {
  vehicles: Vehicle[];
  selectedVehicleId: string | null;
  onSelectVehicle: (vehicle: Vehicle) => void;
  onManageStations: () => void;
}

export function TrackingPanel({
  vehicles,
  selectedVehicleId,
  onSelectVehicle,
  onManageStations,
}: TrackingPanelProps) {
  const [query, setQuery] = useState('');
  const [filterTab, setFilterTab] = useState<VehicleFilterTab>('ALL');

  const counts = useMemo(() => {
    let running = 0;
    let delayed = 0;
    let atStation = 0;

    vehicles.forEach((v) => {
      if (v.status === 'RUNNING') running++;
      else if (v.status === 'DELAYED') delayed++;
      else atStation++;
    });

    return { total: vehicles.length, running, delayed, atStation };
  }, [vehicles]);

  const filteredVehicles = useMemo(() => {
    const q = query.trim().toLowerCase();
    return vehicles.filter((v) => {
      if (filterTab === 'RUNNING' && v.status !== 'RUNNING') return false;
      if (filterTab === 'DELAYED' && v.status !== 'DELAYED') return false;

      if (q) {
        const matchPlate = v.plateNumber.toLowerCase().includes(q);
        const matchDriver = v.driverName.toLowerCase().includes(q);
        const matchRoute = v.routeName.toLowerCase().includes(q);
        const matchStation = v.nextStationName.toLowerCase().includes(q);
        return matchPlate || matchDriver || matchRoute || matchStation;
      }
      return true;
    });
  }, [filterTab, query, vehicles]);

  return (
    <aside className="operations-panel" aria-label="Theo dõi đội xe">
      <div className="operations-panel-header">
        <div>
          <div className="panel-eyebrow">Tổng quan phương tiện</div>
          <h2>Theo dõi đội xe</h2>
        </div>
        <span className="count-badge tabular-numbers">{counts.total} xe</span>
      </div>

      {/* Chỉ số tổng quan đội xe */}
      <div className="fleet-metrics" aria-label="Tổng quan đội xe">
        <div className="metric-box success">
          <BusFront size={16} />
          <strong className="tabular-numbers">{counts.running}</strong>
          <span>Đang chạy</span>
        </div>
        <div className="metric-box warning">
          <Clock3 size={16} />
          <strong className="tabular-numbers">{counts.delayed}</strong>
          <span>Trễ lịch</span>
        </div>
        <div className="metric-box info">
          <Radio size={16} />
          <strong className="tabular-numbers">{counts.total}</strong>
          <span>Kết nối</span>
        </div>
      </div>

      {vehicles.length === 0 ? (
        /* Empty state khi chưa có xe/telemetry kết nối */
        <div className="operations-empty">
          <div className="operations-empty-icon">
            <Route size={28} />
          </div>
          <span className="empty-state-label">CHƯA KẾT NỐI</span>
          <strong>Đội xe của bạn sẽ ở đây</strong>
          <p>
            Chưa kết nối nguồn vị trí xe. Bạn có thể bắt đầu bằng việc thiết lập trạm và tuyến đường.
          </p>
          <button type="button" className="primary-action" onClick={onManageStations}>
            <MapPinned size={16} /> Thiết lập trạm
          </button>
        </div>
      ) : (
        <>
          {/* Tabs lọc */}
          <div className="vehicle-filter-tabs" role="tablist" aria-label="Lọc trạng thái xe">
            <button
              type="button"
              role="tab"
              className={`station-tab-btn ${filterTab === 'ALL' ? 'active' : ''}`}
              onClick={() => setFilterTab('ALL')}
              aria-selected={filterTab === 'ALL'}
            >
              Tất cả ({counts.total})
            </button>
            <button
              type="button"
              role="tab"
              className={`station-tab-btn ${filterTab === 'RUNNING' ? 'active' : ''}`}
              onClick={() => setFilterTab('RUNNING')}
              aria-selected={filterTab === 'RUNNING'}
            >
              Đang chạy ({counts.running})
            </button>
            <button
              type="button"
              role="tab"
              className={`station-tab-btn ${filterTab === 'DELAYED' ? 'active' : ''}`}
              onClick={() => setFilterTab('DELAYED')}
              aria-selected={filterTab === 'DELAYED'}
            >
              Trễ lịch ({counts.delayed})
            </button>
          </div>

          {/* Ô tìm kiếm */}
          <label className="station-search">
            <Search size={15} aria-hidden="true" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm theo biển số, tài xế, trạm..."
              aria-label="Tìm kiếm xe"
            />
          </label>

          {/* Danh sách thẻ xe */}
          <div className="vehicle-list" aria-live="polite">
            {filteredVehicles.length === 0 && (
              <div className="station-empty compact">Không tìm thấy phương tiện phù hợp.</div>
            )}

            {filteredVehicles.map((vehicle) => {
              const isSelected = selectedVehicleId === vehicle.id;
              const isDelayed = vehicle.status === 'DELAYED';

              return (
                <div
                  key={vehicle.id}
                  className={`vehicle-card ${isSelected ? 'selected' : ''} ${isDelayed ? 'delayed' : ''}`}
                  onClick={() => onSelectVehicle(vehicle)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onSelectVehicle(vehicle);
                    }
                  }}
                  aria-pressed={isSelected}
                >
                  <div className="vehicle-card-header">
                    <div className="vehicle-plate-badge">
                      <span className={`vehicle-live-dot ${isDelayed ? 'delayed' : 'active'}`} />
                      <strong>{vehicle.plateNumber}</strong>
                    </div>
                    <div className="vehicle-speed-tag tabular-numbers">
                      <Gauge size={12} />
                      <span>{vehicle.speedKmh} km/h</span>
                    </div>
                  </div>

                  <div className="vehicle-card-sub">
                    <span className="vehicle-model">{vehicle.model}</span>
                    <span className="vehicle-driver">• {vehicle.driverName}</span>
                  </div>

                  <div className="vehicle-card-next">
                    <div className="next-station-row">
                      <MapPin size={12} className="next-icon" />
                      <span className="next-name" title={vehicle.nextStationName}>
                        {vehicle.nextStationName}
                      </span>
                      <span className="next-eta tabular-numbers">ETA ~{vehicle.etaMinutes}p</span>
                    </div>

                    <div className="vehicle-progress-track">
                      <div
                        className="vehicle-progress-fill"
                        style={{ width: `${Math.min(100, Math.max(0, vehicle.tripProgressPercent))}%` }}
                      />
                    </div>
                  </div>

                  {isDelayed && (
                    <div className="vehicle-warning-banner">
                      <AlertTriangle size={12} />
                      <span>Chậm lịch trình do mật độ giao thông cao</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* Footer chuyển sang Quản lý trạm */}
      <div className="operations-panel-footer">
        <Radio size={15} /><span>Vị trí, vận tốc và ETA cần nguồn dữ liệu xe.</span>
      </div>
    </aside>
  );
}
