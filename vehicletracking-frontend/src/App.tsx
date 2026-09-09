import { MapComponent } from './components/MapComponent';
import { Navigation } from 'lucide-react';

export default function App() {
  return (
    <div style={{ position: 'relative', width: '100vw', height: '100vh', overflow: 'hidden' }}>
      {/* Thanh Header Glassmorphism */}
      <header className="header-glass">
        <div className="brand-section">
          <div className="brand-icon">
            <Navigation size={20} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span className="brand-title">Vehicle Tracking</span>
              <span className="brand-tag">Google Maps</span>
            </div>
            <div style={{ fontSize: '0.7rem', color: '#64748b' }}>
              Bản đồ TP. Hồ Chí Minh • Hệ thống sẵn sàng
            </div>
          </div>
        </div>

        <div className="header-status">
          <div className="status-badge">
            <div className="pulse-dot" />
            <span>Hệ Thống Trực Tuyến</span>
          </div>
        </div>
      </header>

      {/* Bản Đồ Chính */}
      <MapComponent />
    </div>
  );
}
