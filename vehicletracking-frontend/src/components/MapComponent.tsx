import { useEffect, useRef, useState, type FC } from 'react';
import L from 'leaflet';
import { MapTheme } from '../types/map';
import { MapControls } from './MapControls';

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

export const MapComponent: FC = () => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);

  const [theme, setTheme] = useState<MapTheme>('google-roadmap');
  const [mouseCoords, setMouseCoords] = useState<{ lat: number; lng: number } | null>(null);

  // Khởi tạo bản đồ Leaflet một lần duy nhất
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: HCMC_CENTER,
      zoom: 13,
      zoomControl: false,
    });

    // Zoom control ở góc trên bên phải
    L.control.zoom({ position: 'topright' }).addTo(map);

    // Lắng nghe sự kiện di chuyển chuột để cập nhật tọa độ thời gian thực
    map.on('mousemove', (e: L.LeafletMouseEvent) => {
      setMouseCoords({ lat: e.latlng.lat, lng: e.latlng.lng });
    });

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Cập nhật lớp gạch Tile Layer (Google Maps) theo theme
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
      tileLayerRef.current = null;
    }

    const layerType = theme === 'google-satellite' ? 'y' : 'm';
    const tileUrl = `https://{s}.google.com/vt/lyrs=${layerType}&x={x}&y={y}&z={z}`;

    const newTileLayer = L.tileLayer(tileUrl, {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
      className: theme === 'google-dark' ? 'dark-map-tiles' : '',
      attribution: '&copy; Google Maps',
    });

    newTileLayer.addTo(map);
    tileLayerRef.current = newTileLayer;
  }, [theme]);

  // Về trung tâm TP. Hồ Chí Minh
  const handleResetCenter = () => {
    if (mapInstanceRef.current) {
      mapInstanceRef.current.setView(HCMC_CENTER, 13, { animate: true });
    }
  };

  return (
    <div className="map-viewport">
      <div ref={mapContainerRef} style={{ width: '100%', height: '100%' }} />

      <MapControls
        theme={theme}
        onThemeChange={setTheme}
        onResetCenter={handleResetCenter}
        mouseCoords={mouseCoords}
      />
    </div>
  );
};
