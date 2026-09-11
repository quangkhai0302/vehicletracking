import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Compass,
  MapPin,
  Maximize2,
  Navigation,
  Phone,
  User,
  X,
} from 'lucide-react';
import type { Vehicle } from '../types/vehicle';

interface VehicleDrawerProps {
  vehicle: Vehicle | null;
  following: boolean;
  onToggleFollow: () => void;
  onFitRoute: () => void;
  onClose: () => void;
}

export function VehicleDrawer({
  vehicle,
  following,
  onToggleFollow,
  onFitRoute,
  onClose,
}: VehicleDrawerProps) {
  if (!vehicle) return null;

  const isDelayed = vehicle.status === 'DELAYED';

  return (
    <aside className="vehicle-drawer" aria-label="Chi tiết phương tiện">
      {/* Header drawer */}
      <div className="drawer-header">
        <div>
          <div className="drawer-eyebrow-row">
            <span className="panel-eyebrow">Giám sát xe trực tuyến</span>
            <span className={`vehicle-status-pill ${isDelayed ? 'delayed' : 'running'}`}>
              <span className="status-dot-pulse" />
              {isDelayed ? 'Trễ lịch trình' : 'Đang vận hành'}
            </span>
          </div>
          <h2>{vehicle.plateNumber}</h2>
          <div className="vehicle-sub-header">
            <span>{vehicle.model}</span>
            <span className="bullet-sep">•</span>
            <span>{vehicle.routeName}</span>
          </div>
        </div>

        <button
          type="button"
          className="icon-action"
          onClick={onClose}
          aria-label="Đóng panel chi tiết xe"
          title="Đóng"
        >
          <X size={18} />
        </button>
      </div>

      {/* Cảnh báo nếu trễ lịch */}
      {isDelayed && (
        <div className="station-alert drawer-alert warning" role="alert">
          <AlertTriangle size={15} />
          <span>Phương tiện đang di chuyển chậm hơn dự kiến 4 phút do mật độ giao thông.</span>
        </div>
      )}

      <div className="drawer-content-scroll">
        {/* Khối chỉ số vận tốc (Speedometer section) */}
        <section className="speedometer-card">
          <div className="speedometer-main">
            <div className="speed-display">
              <span className="speed-number tabular-numbers">{vehicle.speedKmh}</span>
              <span className="speed-unit">km/h</span>
            </div>
            <div className="speed-meta">
              <span className="speed-label">VẬN TỐC TỨC THỜI</span>
              <div className="speed-comparison">
                <span>Mục tiêu: {vehicle.desiredSpeedKmh} km/h</span>
                <span>•</span>
                <span>Tối đa: {vehicle.speedLimitKmh} km/h</span>
              </div>
            </div>
          </div>

          <div className="speed-progress-track">
            <div
              className={`speed-progress-fill ${vehicle.speedKmh > vehicle.speedLimitKmh ? 'danger' : ''}`}
              style={{ width: `${Math.min(100, (vehicle.speedKmh / vehicle.speedLimitKmh) * 100)}%` }}
            />
          </div>
        </section>

        {/* Thẻ trạm tiếp theo & Tiến độ hành trình */}
        <section className="next-station-card">
          <div className="next-card-header">
            <div className="next-card-title">
              <MapPin size={16} className="text-cyan" />
              <span>TRẠM TIẾP THEO</span>
            </div>
            <span className="next-eta-badge tabular-numbers">ETA ~{vehicle.etaMinutes} phút</span>
          </div>

          <h3 className="next-station-name">{vehicle.nextStationName}</h3>

          <div className="next-card-metrics tabular-numbers">
            <div>
              <span className="metric-label">Cự ly còn lại</span>
              <span className="metric-val">{vehicle.distanceToNextMeters} m</span>
            </div>
            <div>
              <span className="metric-label">Tiến độ tuyến</span>
              <span className="metric-val">{vehicle.tripProgressPercent}%</span>
            </div>
            <div>
              <span className="metric-label">Hướng di chuyển</span>
              <span className="metric-val">{vehicle.heading}°</span>
            </div>
          </div>

          <div className="trip-progress-bar">
            <div className="trip-progress-fill" style={{ width: `${vehicle.tripProgressPercent}%` }} />
          </div>
        </section>

        {/* Thông tin tài xế */}
        <section className="driver-card">
          <div className="driver-info">
            <div className="driver-avatar">
              <User size={18} />
            </div>
            <div>
              <span className="driver-label">TÀI XẾ PHỤ TRÁCH</span>
              <strong className="driver-name">{vehicle.driverName}</strong>
            </div>
          </div>
          <a
            href={`tel:${vehicle.driverPhone.replace(/\s+/g, '')}`}
            className="driver-phone-btn"
            title={`Gọi ${vehicle.driverPhone}`}
          >
            <Phone size={14} />
            <span>{vehicle.driverPhone}</span>
          </a>
        </section>

        {/* Timeline lộ trình theo Mục 18 docs/design.md */}
        <section className="timeline-section">
          <div className="timeline-title-row">
            <Clock size={15} />
            <span>LỊCH TRÌNH CÁC TRẠM</span>
          </div>

          <div className="timeline-list">
            {vehicle.timeline.map((stop, idx) => {
              const isPassed = stop.status === 'PASSED';
              const isCurrent = stop.status === 'CURRENT';

              return (
                <div
                  key={stop.stationId || idx}
                  className={`timeline-item ${isPassed ? 'passed' : ''} ${isCurrent ? 'current' : ''}`}
                >
                  <div className="timeline-marker-col">
                    <div className="timeline-node">
                      {isPassed ? <CheckCircle2 size={12} /> : isCurrent ? <Compass size={12} /> : null}
                    </div>
                    {idx < vehicle.timeline.length - 1 && <div className="timeline-line" />}
                  </div>

                  <div className="timeline-info">
                    <strong className="timeline-station-name">{stop.stationName}</strong>
                    <div className="timeline-times tabular-numbers">
                      <span className="time-planned">Dự kiến: {stop.plannedTime}</span>
                      <span className="time-sep">•</span>
                      <span className={`time-actual ${isPassed ? 'passed' : 'eta'}`}>
                        {isPassed ? `Đã đến: ${stop.actualOrEtaTime}` : `ETA: ${stop.actualOrEtaTime}`}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {/* Hành động dưới chân drawer */}
      <div className="drawer-actions-footer">
        <button
          type="button"
          className={`follow-vehicle-btn ${following ? 'following' : ''}`}
          onClick={onToggleFollow}
          aria-pressed={following}
        >
          <Navigation size={14} className={following ? 'spin-subtle' : ''} />
          <span>{following ? 'Đang bám theo xe' : 'Bám theo xe này'}</span>
        </button>

        <button
          type="button"
          className="fit-route-btn"
          onClick={onFitRoute}
          title="Xem toàn bộ lộ trình tuyến"
          aria-label="Xem toàn bộ tuyến đường"
        >
          <Maximize2 size={14} />
          <span>Toàn tuyến</span>
        </button>
      </div>
    </aside>
  );
}
