import type { FC } from 'react';
import { MapTheme } from '../types/map';
import { Map, Satellite, Moon, Navigation, Compass } from 'lucide-react';

interface MapControlsProps {
  theme: MapTheme;
  onThemeChange: (theme: MapTheme) => void;
  onResetCenter: () => void;
  mouseCoords: { lat: number; lng: number } | null;
}

export const MapControls: FC<MapControlsProps> = ({
  theme,
  onThemeChange,
  onResetCenter,
  mouseCoords,
}) => {
  return (
    <>
      {/* Tọa độ góc dưới bên trái */}
      <div className="bottom-left-bar">
        <div className="coordinate-badge">
          <Compass size={14} color="#00f0ff" />
          <span>
            {mouseCoords
              ? `Tọa độ: ${mouseCoords.lat.toFixed(5)}, ${mouseCoords.lng.toFixed(5)}`
              : 'Di chuột trên bản đồ để xem tọa độ'}
          </span>
        </div>
      </div>

      {/* Bảng điều khiển chọn bản đồ nền góc dưới bên phải */}
      <div className="map-controls-panel">
        <div className="control-card">
          <div className="control-label">
            <span>Bản đồ nền (Google Maps)</span>
            <button
              onClick={onResetCenter}
              className="segment-btn"
              style={{ padding: '2px 8px', fontSize: '0.7rem', flex: 'none' }}
              title="Về trung tâm TP.HCM"
            >
              <Navigation size={12} /> TP.HCM
            </button>
          </div>

          <div className="segmented-group">
            <button
              className={`segment-btn ${theme === 'google-roadmap' ? 'active' : ''}`}
              onClick={() => onThemeChange('google-roadmap')}
            >
              <Map size={14} /> Đường Bộ
            </button>

            <button
              className={`segment-btn ${theme === 'google-satellite' ? 'active' : ''}`}
              onClick={() => onThemeChange('google-satellite')}
            >
              <Satellite size={14} /> Vệ Tinh
            </button>

            <button
              className={`segment-btn ${theme === 'google-dark' ? 'active' : ''}`}
              onClick={() => onThemeChange('google-dark')}
            >
              <Moon size={14} /> Ban Đêm
            </button>
          </div>
        </div>
      </div>
    </>
  );
};
