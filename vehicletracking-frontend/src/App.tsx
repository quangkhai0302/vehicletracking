import { useState } from 'react';
import { BusFront, MapPinned, Navigation, Radio } from 'lucide-react';
import { MapComponent } from './components/MapComponent';
import type { WorkspaceMode } from './types/workspace';

export default function App() {
  const [workspace, setWorkspace] = useState<WorkspaceMode>('tracking');

  return (
    <div className="application-shell">
      <header className="header-glass">
        <div className="brand-section">
          <div className="brand-icon">
            <Navigation size={20} />
          </div>
          <div>
            <div className="brand-line">
              <span className="brand-title">Vehicle Tracking</span>
              <span className="brand-tag">Operations</span>
            </div>
            <div className="brand-subtitle">Trung tâm điều hành & theo dõi đội xe</div>
          </div>
        </div>

        <nav className="workspace-navigation" aria-label="Không gian làm việc">
          <button
            className={workspace === 'tracking' ? 'active' : ''}
            onClick={() => setWorkspace('tracking')}
            aria-pressed={workspace === 'tracking'}
          >
            <BusFront size={16} /> Theo dõi xe
          </button>
          <button
            className={workspace === 'stations' ? 'active' : ''}
            onClick={() => setWorkspace('stations')}
            aria-pressed={workspace === 'stations'}
          >
            <MapPinned size={16} /> Quản lý trạm
          </button>
        </nav>

        <div className="header-status">
          <div className={`status-badge ${workspace === 'tracking' ? 'waiting' : 'ready'}`}>
            <Radio size={14} />
            <span>
              {workspace === 'tracking'
                ? 'Chờ nguồn telemetry'
                : 'Chế độ quản lý trạm'}
            </span>
          </div>
        </div>
      </header>

      <MapComponent workspace={workspace} onWorkspaceChange={setWorkspace} />
    </div>
  );
}
