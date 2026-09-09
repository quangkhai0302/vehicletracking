import { useEffect, useRef, useState, type FC } from 'react';
import L from 'leaflet';
import { MapTheme } from '../types/map';
import { MapControls } from './MapControls';

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

export const MapComponent: FC = () => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const coordRef = useRef<HTMLSpanElement>(null);

  const [theme, setTheme] = useState<MapTheme>('google-roadmap');

  // Khởi tạo bản đồ Leaflet một lần duy nhất với các cấu hình tối ưu độ mượt
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: HCMC_CENTER,
      zoom: 13,
      zoomControl: false,
      preferCanvas: true,
      zoomSnap: 1, // Cố định cấp zoom nguyên để triệt tiêu hoàn toàn các đường kẻ ranh giới ô vuông giữa các tile
      zoomDelta: 1,
      wheelPxPerZoomLevel: 120,
      inertia: true, // Trượt quán tính tự nhiên khi vuốt/kéo
      inertiaDeceleration: 3400,
      inertiaMaxSpeed: 2000,
    });

    // Zoom control ở góc trên bên phải
    L.control.zoom({ position: 'topright' }).addTo(map);

    // Cập nhật tọa độ thời gian thực trực tiếp qua DOM và requestAnimationFrame
    // -> Loại bỏ hoàn toàn re-render React khi di chuột, giúp map cực kỳ mượt mà
    let rafId: number | null = null;
    let pendingCoords: { lat: number; lng: number } | null = null;

    const onMouseMove = (e: L.LeafletMouseEvent) => {
      pendingCoords = { lat: e.latlng.lat, lng: e.latlng.lng };
      if (!rafId) {
        rafId = requestAnimationFrame(() => {
          if (coordRef.current && pendingCoords) {
            coordRef.current.textContent = `Tọa độ: ${pendingCoords.lat.toFixed(5)}, ${pendingCoords.lng.toFixed(5)}`;
          }
          rafId = null;
        });
      }
    };

    const onMouseOut = () => {
      if (rafId) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
      if (coordRef.current) {
        coordRef.current.textContent = 'Di chuột trên bản đồ để xem tọa độ';
      }
    };

    map.on('mousemove', onMouseMove);
    map.on('mouseout', onMouseOut);

    mapInstanceRef.current = map;

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      map.off('mousemove', onMouseMove);
      map.off('mouseout', onMouseOut);
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Cập nhật lớp gạch Tile Layer (Google Maps) theo theme kèm ngôn ngữ & vùng địa lý Việt Nam
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
      tileLayerRef.current = null;
    }

    const layerType = theme === 'google-satellite' ? 'y' : 'm';
    // Thêm hl=vi&gl=VN:
    // - hl=vi: Ưu tiên ngôn ngữ Tiếng Việt (loại bỏ nhãn tiếng Thái/ngoại ngữ khu vực như 'ฮานอย' khi zoom nhỏ)
    // - gl=VN: Cấu hình địa lý Việt Nam
    const tileUrl = `https://{s}.google.com/vt/lyrs=${layerType}&hl=vi&gl=VN&x={x}&y={y}&z={z}`;

    const newTileLayer = L.tileLayer(tileUrl, {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
      className: theme === 'google-dark' ? 'dark-map-tiles' : '',
      attribution: '&copy; Google Maps',
      updateWhenZooming: true, // Tải gạch liên tục ngay cả trong khi zoom
      updateWhenIdle: false, // Tải gạch liên tục trong khi đang rê kéo bản đồ (không chờ dừng chuột)
      keepBuffer: 6, // Bộ đệm 6 hàng/cột gạch sẵn xung quanh để kéo không bị mảng xám
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
      <div
        ref={mapContainerRef}
        style={{
          width: '100%',
          height: '100%',
          background: theme === 'google-roadmap' ? '#aad3df' : '#070b14',
        }}
      />

      <MapControls
        theme={theme}
        onThemeChange={setTheme}
        onResetCenter={handleResetCenter}
        coordRef={coordRef}
      />
    </div>
  );
};
