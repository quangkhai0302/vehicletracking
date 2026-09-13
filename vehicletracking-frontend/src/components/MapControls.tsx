import type { RefObject } from 'react';
import type { MapTheme } from '../types/map';
import { Compass, Layers, Minus, Navigation, Plus, Scan } from 'lucide-react';

interface MapControlsProps {
  theme: MapTheme; onThemeChange: (theme: MapTheme) => void;
  onResetCenter: () => void; onZoomIn: () => void; onZoomOut: () => void;
  onFit: () => void; canFit: boolean;
  showStations: boolean; showRoutes: boolean;
  onToggleStations: () => void; onToggleRoutes: () => void;
  coordRef: RefObject<HTMLSpanElement | null>;
}

export function MapControls({ theme, onThemeChange, onResetCenter, onZoomIn, onZoomOut, onFit, canFit, showStations, showRoutes, onToggleStations, onToggleRoutes, coordRef }: MapControlsProps) {
  return <>
    <div className="coordinate-badge"><Compass size={12} /><span ref={coordRef}>Di chuột trên bản đồ để xem tọa độ</span></div>
    <div className="canvas-map-tools glass-panel" aria-label="Điều khiển bản đồ">
      <button title="Thu phóng toàn tuyến hoặc các trạm" aria-label="Vừa khung lộ trình" onClick={onFit} disabled={!canFit}><Scan size={18} /><span>Vừa khung</span></button>
      <details className="map-layers" onKeyDown={event => { if (event.key === 'Escape') { event.currentTarget.open = false; event.currentTarget.querySelector('summary')?.focus(); } }}>
        <summary aria-label="Lớp bản đồ"><Layers size={17} /><span>Lớp bản đồ</span></summary>
        <div className="map-layer-options">
          <strong>Bản đồ nền</strong>
          <div className="basemap-options">
            {([{ id: 'google-dark', name: 'Ban đêm' }, { id: 'google-roadmap', name: 'Đường bộ' }, { id: 'google-satellite', name: 'Vệ tinh' }] as const).map(item =>
              <button key={item.id} aria-pressed={theme === item.id} onClick={() => onThemeChange(item.id)}>{item.name}</button>)}
          </div>
          <label><input type="checkbox" checked={showStations} onChange={onToggleStations} />Trạm dừng</label>
          <label><input type="checkbox" checked={showRoutes} onChange={onToggleRoutes} />Tuyến & điểm nháp</label>
          <label><input type="checkbox" disabled />Giao thông trực tiếp</label>
          <p>Chưa kết nối nguồn traffic và sự cố.</p>
          <button onClick={onResetCenter}><Navigation size={14} />Về TP. Hồ Chí Minh</button>
        </div>
      </details>
      <div className="zoom-buttons"><button onClick={onZoomOut} aria-label="Thu nhỏ bản đồ"><Minus size={18} /></button><button onClick={onZoomIn} aria-label="Phóng to bản đồ"><Plus size={18} /></button></div>
    </div>
    <div className="map-data-note"><span />Lộ trình <i />Trạm dừng</div>
  </>;
}
