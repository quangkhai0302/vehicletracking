import { BookOpen, BusFront, Navigation, Play, Route, Radio } from 'lucide-react';
import type { WorkspaceMode } from '../../types/workspace';

export function ModeBar({ mode, onChange, connectionLabel = 'Chưa có vị trí xe' }: { mode: WorkspaceMode; onChange: (mode: WorkspaceMode) => void; connectionLabel?: string }) {
  return <header className="mode-bar glass-panel" data-map-edge="top">
    <a className="canvas-brand" href="#main-map" aria-label="Vehicletracking · đến bản đồ">
      <span className="brand-logo-icon"><Navigation size={17} /></span>
      <span>vehicle<span>tracking</span><span className="brand-badge">OPS</span></span>
    </a>
    <nav aria-label="Chế độ vận hành">
      <button aria-pressed={mode === 'tracking'} onClick={() => onChange('tracking')}><BusFront size={16} /><span>Theo dõi</span></button>
      <button aria-pressed={mode === 'routes' || mode === 'stations'} onClick={() => onChange('routes')}><Route size={16} /><span>Tuyến & trạm</span></button>
      <button aria-pressed={mode === 'simulation'} onClick={() => onChange('simulation')}><Play size={16} /><span>Mô phỏng</span></button>
    </nav>
    <a className="guide-link" href="/huong-dan/" target="_blank" rel="noreferrer" aria-label="Mở hướng dẫn sử dụng" title="Hướng dẫn sử dụng"><BookOpen size={15} /><span>Hướng dẫn</span></a>
    <span className="connection-state"><span className="live-beacon-dot" aria-hidden="true" /><Radio size={13} /> <span>{connectionLabel}</span></span>
  </header>;
}
