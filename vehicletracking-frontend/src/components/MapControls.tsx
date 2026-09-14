import { useEffect, useRef, type RefObject } from 'react';
import type { MapTheme } from '../types/map';
import { Compass, Layers, Minus, Navigation, Plus, Scan } from 'lucide-react';

interface MapControlsProps {
  theme: MapTheme;
  onThemeChange: (theme: MapTheme) => void;
  onResetCenter: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFit: () => void;
  canFit: boolean;
  showStations: boolean;
  showRoutes: boolean;
  onToggleStations: () => void;
  onToggleRoutes: () => void;
  showTraffic: boolean;
  onToggleTraffic: () => void;
  trafficMessage: string;
  trafficCanRetry: boolean;
  onRetryTraffic: () => void;
  coordRef: RefObject<HTMLSpanElement | null>;
}

export function MapControls({
  theme,
  onThemeChange,
  onResetCenter,
  onZoomIn,
  onZoomOut,
  onFit,
  canFit,
  showStations,
  showRoutes,
  onToggleStations,
  onToggleRoutes,
  showTraffic,
  onToggleTraffic,
  trafficMessage,
  trafficCanRetry,
  onRetryTraffic,
  coordRef,
}: MapControlsProps) {
  const detailsRef = useRef<HTMLDetailsElement | null>(null);

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      const details = detailsRef.current;
      if (!details || !details.open) return;
      if (!details.contains(event.target as Node)) {
        details.open = false;
      }
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, []);

  return (
    <>
      {/* Google Maps Bottom-Left Layers Control */}
      <div className={`gm-layers-widget ${theme === 'google-dark' ? 'dark' : 'light'}`}>
        <details
          ref={detailsRef}
          className="gm-layers-details"
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.currentTarget.open = false;
              event.currentTarget.querySelector('summary')?.focus();
            }
          }}
        >
          <summary className="gm-layers-summary" aria-label="Lớp bản đồ">
            <Layers size={18} />
            <span>Lớp bản đồ</span>
          </summary>
          <div className="gm-layers-popover">
            <div className="gm-layers-section">
              <span className="gm-section-title">Bản đồ nền</span>
              <div className="gm-basemap-grid">
                {(
                  [
                    { id: 'google-roadmap', name: 'Đường bộ' },
                    { id: 'google-satellite', name: 'Vệ tinh' },
                    { id: 'google-dark', name: 'Ban đêm' },
                  ] as const
                ).map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`gm-basemap-card ${theme === item.id ? 'active' : ''}`}
                    aria-pressed={theme === item.id}
                    onClick={() => onThemeChange(item.id)}
                  >
                    <span className={`gm-basemap-thumb ${item.id}`} />
                    <span className="gm-basemap-label">{item.name}</span>
                  </button>
                ))}
              </div>
            </div>

            <div className="gm-layers-divider" />

            <div className="gm-layers-section">
              <span className="gm-section-title">Chi tiết bản đồ</span>
              <div className="gm-overlays-list">
                <label className="gm-overlay-row">
                  <input type="checkbox" checked={showTraffic} onChange={onToggleTraffic} />
                  <span className="gm-overlay-name">Giao thông trực tiếp</span>
                </label>

                {showTraffic && (
                  <div className="gm-traffic-details">
                    <p className="gm-traffic-status" role="status">
                      {trafficMessage}{' '}
                      {trafficCanRetry && (
                        <button type="button" className="traffic-retry" onClick={onRetryTraffic}>
                          Thử lại
                        </button>
                      )}
                    </p>
                    <div className="traffic-legend" aria-label="Chú giải giao thông">
                      <div className="traffic-legend-bar">
                        <span>Nhanh</span>
                        <div className="traffic-legend-gradient" />
                        <span>Chậm</span>
                      </div>
                      <div className="traffic-legend-items">
                        <span><i className="clear" />Thông thoáng</span>
                        <span><i className="slow" />Chậm</span>
                        <span><i className="congested" />Ùn tắc</span>
                        <span><i className="blocked" />Kẹt / Đóng</span>
                      </div>
                    </div>
                  </div>
                )}

                <label className="gm-overlay-row">
                  <input type="checkbox" checked={showStations} onChange={onToggleStations} />
                  <span className="gm-overlay-name">Trạm dừng</span>
                </label>

                <label className="gm-overlay-row">
                  <input type="checkbox" checked={showRoutes} onChange={onToggleRoutes} />
                  <span className="gm-overlay-name">Tuyến & điểm nháp</span>
                </label>
              </div>
            </div>
          </div>
        </details>
      </div>

      {/* Google Maps Bottom-Right Navigation Cluster */}
      <div className={`gm-control-stack ${theme === 'google-dark' ? 'dark' : 'light'}`} aria-label="Điều khiển bản đồ">
        <button
          type="button"
          className="gm-btn-square"
          title="Thu phóng vừa toàn bộ tuyến hoặc các trạm"
          aria-label="Vừa khung lộ trình"
          onClick={onFit}
          disabled={!canFit}
        >
          <Scan size={18} />
        </button>
        <button
          type="button"
          className="gm-btn-square"
          title="Về trung tâm TP. Hồ Chí Minh"
          aria-label="Về TP. Hồ Chí Minh"
          onClick={onResetCenter}
        >
          <Navigation size={18} />
        </button>
        <div className="gm-zoom-group">
          <button
            type="button"
            className="gm-zoom-btn"
            onClick={onZoomIn}
            aria-label="Phóng to bản đồ"
            title="Phóng to"
          >
            <Plus size={18} />
          </button>
          <div className="gm-zoom-divider" />
          <button
            type="button"
            className="gm-zoom-btn"
            onClick={onZoomOut}
            aria-label="Thu nhỏ bản đồ"
            title="Thu nhỏ"
          >
            <Minus size={18} />
          </button>
        </div>
      </div>

      {/* Google Maps Floating Live Traffic Pill (Bottom-Center - authentic Google Maps design) */}
      <div className={`gm-traffic-floating-pill ${theme === 'google-dark' ? 'dark' : 'light'}`} role="region" aria-label="Thông tin giao thông HERE">
        <div className="gm-traffic-pill-status">
          <span className={`gm-traffic-live-dot ${showTraffic ? 'active' : ''}`} />
          <div className="gm-traffic-pill-label">
            <span className="gm-traffic-title">Giao thông trực tiếp</span>
            <span className="gm-traffic-sub">{showTraffic ? 'Đang hoạt động' : 'Tạm tắt'}</span>
          </div>
        </div>

        <div className="gm-traffic-pill-divider" />

        <div className="gm-traffic-pill-legend">
          <span className="gm-legend-tag fast">Nhanh</span>
          <div className="gm-legend-bar-gradient" />
          <span className="gm-legend-tag slow">Chậm</span>
        </div>

        <div className="gm-traffic-pill-divider" />

        <button
          type="button"
          className={`gm-traffic-toggle-switch ${showTraffic ? 'on' : 'off'}`}
          onClick={onToggleTraffic}
          aria-pressed={showTraffic}
          title={showTraffic ? 'Tắt hiển thị lớp giao thông' : 'Bật hiển thị lớp giao thông'}
        >
          <span className="gm-switch-track">
            <span className="gm-switch-thumb" />
          </span>
        </button>
      </div>

      {/* Subtle Google Maps coordinate badge */}
      <div className={`gm-coord-badge ${theme === 'google-dark' ? 'dark' : 'light'}`}>
        <Compass size={11} />
        <span ref={coordRef}>Di chuột trên bản đồ để xem tọa độ</span>
      </div>
    </>
  );
}
