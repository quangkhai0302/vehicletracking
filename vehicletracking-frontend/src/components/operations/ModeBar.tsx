import { BusFront, Navigation, Play, Route, Radio } from 'lucide-react';
import type { WorkspaceMode } from '../../types/workspace';

export function ModeBar({ mode, onChange, connectionLabel = 'Chưa có vị trí xe' }: { mode: WorkspaceMode; onChange: (mode: WorkspaceMode) => void; connectionLabel?: string }) {
  return <header className="mode-bar glass-panel" data-map-edge="top">
    <a className="canvas-brand" href="#main-map" aria-label="Vehicletracking · đến bản đồ"><Navigation size={21} /><span>vehicle<span>tracking</span></span></a>
    <nav aria-label="Chế độ vận hành">
      <button aria-pressed={mode === 'tracking'} onClick={() => onChange('tracking')}><BusFront size={17} /><span>Theo dõi</span></button>
      <button aria-pressed={mode === 'routes' || mode === 'stations'} onClick={() => onChange('routes')}><Route size={17} /><span>Tuyến & trạm</span></button>
      <button aria-pressed={mode === 'simulation'} onClick={() => onChange('simulation')}><Play size={17} /><span>Mô phỏng</span></button>
    </nav>
    <span className="connection-state"><Radio size={14} /> {connectionLabel}</span>
  </header>;
}
