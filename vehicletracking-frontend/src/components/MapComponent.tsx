import React, { useEffect, useRef } from 'react';
import L from 'leaflet';
import { Station, Route, VehicleTelemetry, TrafficIncident } from '../types';

interface MapComponentProps {
  stations: Station[];
  route: Route | null;
  vehicleTelemetry: VehicleTelemetry | null;
  incidents: TrafficIncident[];
  onMapClick?: (latlng: L.LatLng) => void;
  clickMode?: string | null;
}

export default function MapComponent({
  stations = [],
  route = null,
  vehicleTelemetry = null,
  incidents = [],
  onMapClick,
  clickMode = null,
}: MapComponentProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const vehicleMarkerRef = useRef<L.Marker | null>(null);
  const routePolylineRef = useRef<L.Polyline | null>(null);
  const stationMarkersRef = useRef<L.Marker[]>([]);
  const incidentLayersRef = useRef<L.Layer[]>([]);

  // Khởi tạo bản đồ Leaflet
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: [10.795, 106.705],
      zoom: 13,
      zoomControl: false,
    });

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; <a href="https://carto.com/">CARTO</a> | &copy; OpenStreetMap',
      maxZoom: 19,
      subdomains: 'abcd',
    }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Xử lý click chọn vị trí trên bản đồ
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    const handleClick = (e: L.LeafletMouseEvent) => {
      if (onMapClick && clickMode) {
        onMapClick(e.latlng);
      }
    };

    map.on('click', handleClick);
    return () => {
      map.off('click', handleClick);
    };
  }, [onMapClick, clickMode]);

  // Cập nhật trạm dừng (Station Markers)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    stationMarkersRef.current.forEach(m => map.removeLayer(m));
    stationMarkersRef.current = [];

    const stationsToCheck = vehicleTelemetry?.stationsEta || [];

    stations.forEach((station, index) => {
      const etaInfo = stationsToCheck.find(s => s.stationId === station.id);
      const isChecked = etaInfo?.status === 'CHECKED_IN';
      const isTarget = vehicleTelemetry?.targetStationId === station.id;
      const isStart = index === 0;
      const isEnd = index === stations.length - 1;
      const stopNumber = index + 1;

      const iconHtml = `
        <div class="station-pin ${isStart ? 'start' : isEnd ? 'end' : ''} ${isChecked ? 'checked' : ''}">
          <div class="station-pin-badge" style="${isTarget ? 'border-color: #00f0ff; box-shadow: 0 0 12px #00f0ff; transform: scale(1.15);' : ''}">
            ${isChecked ? '✓' : stopNumber}
          </div>
          <div style="background: rgba(15,23,42,0.85); backdrop-filter: blur(8px); padding: 2px 6px; border-radius: 6px; font-size: 10px; font-weight: 600; color: #f8fafc; white-space: nowrap; margin-top: 3px; border: 1px solid rgba(255,255,255,0.1);">
            ${station.name}
          </div>
        </div>
      `;

      const customIcon = L.divIcon({
        className: 'custom-station-icon',
        html: iconHtml,
        iconSize: [120, 50],
        iconAnchor: [60, 15],
      });

      const marker = L.marker([station.latitude, station.longitude], { icon: customIcon })
        .addTo(map)
        .bindPopup(`
          <div style="font-family: var(--font-body); color: #0f172a; min-width: 180px;">
            <strong style="font-size: 13px; color: #1e293b;">${station.name}</strong><br/>
            <span style="font-size: 11px; color: #64748b;">Mã trạm: ${station.code}</span><br/>
            <span style="font-size: 11px; color: #64748b;">Loại: ${station.stationType}</span><br/>
            <span style="font-size: 11px; color: #64748b;">Bán kính checkin: ${station.radiusMeters}m</span>
          </div>
        `);

      stationMarkersRef.current.push(marker);
    });
  }, [stations, vehicleTelemetry]);

  // Cập nhật đường nối Tuyến đường (Polyline)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (routePolylineRef.current) {
      map.removeLayer(routePolylineRef.current);
      routePolylineRef.current = null;
    }

    if (stations && stations.length >= 2) {
      const latlngs = stations.map(s => [s.latitude, s.longitude] as [number, number]);

      const polyline = L.polyline(latlngs, {
        color: '#00f0ff',
        weight: 5,
        opacity: 0.85,
        lineCap: 'round',
        lineJoin: 'round',
        dashArray: '1, 8',
      }).addTo(map);

      routePolylineRef.current = polyline;

      if (!vehicleTelemetry) {
        map.fitBounds(polyline.getBounds(), { padding: [60, 60] });
      }
    }
  }, [stations]);

  // Cập nhật Sự cố giao thông (Incidents)
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    incidentLayersRef.current.forEach(layer => map.removeLayer(layer));
    incidentLayersRef.current = [];

    incidents.filter(inc => inc.active).forEach(incident => {
      const isCongestion = incident.type === 'CONGESTION';
      const color = isCongestion ? '#ef4444' : '#f59e0b';

      const circle = L.circle([incident.latitude, incident.longitude], {
        color: color,
        fillColor: color,
        fillOpacity: 0.25,
        radius: incident.radiusMeters || 200,
        weight: 2,
        dashArray: '4, 6',
      }).addTo(map);

      const iconHtml = `
        <div style="background: ${color}; width: 26px; height: 26px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white; box-shadow: 0 0 12px ${color}; font-size: 13px; font-weight: 800;">
          !
        </div>
      `;
      const icon = L.divIcon({
        html: iconHtml,
        className: 'incident-icon',
        iconSize: [26, 26],
        iconAnchor: [13, 13],
      });

      const marker = L.marker([incident.latitude, incident.longitude], { icon })
        .addTo(map)
        .bindPopup(`
          <div style="font-family: var(--font-body); color: #0f172a; min-width: 180px;">
            <strong style="color: ${color};">${incident.title}</strong><br/>
            <span style="font-size: 11px; color: #475569;">Loại: ${incident.type}</span><br/>
            <span style="font-size: 11px; color: #475569;">Giảm tốc: ${incident.speedReductionPercent}%</span><br/>
            <p style="font-size: 11px; margin-top: 4px;">${incident.description || ''}</p>
          </div>
        `);

      incidentLayersRef.current.push(circle, marker);
    });
  }, [incidents]);

  // Cập nhật Xe và góc xoay realtime
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !vehicleTelemetry || !vehicleTelemetry.latitude) return;

    const { latitude, longitude, heading = 0, plateNumber, speed = 0 } = vehicleTelemetry;

    const vehicleIconHtml = `
      <div class="vehicle-marker-container">
        <div class="vehicle-marker-halo"></div>
        <div class="vehicle-marker-icon" style="transform: rotate(${heading}deg);">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.5 2.8C2.1 11.2 2 11.7 2 12.2V16c0 .6.4 1 1 1h2"/>
            <circle cx="7" cy="17" r="2"/>
            <path d="M9 17h6"/>
            <circle cx="17" cy="17" r="2"/>
          </svg>
        </div>
      </div>
    `;

    const customIcon = L.divIcon({
      className: 'custom-vehicle-icon',
      html: vehicleIconHtml,
      iconSize: [48, 48],
      iconAnchor: [24, 24],
    });

    if (!vehicleMarkerRef.current) {
      const marker = L.marker([latitude, longitude], { icon: customIcon, zIndexOffset: 1000 })
        .addTo(map)
        .bindTooltip(`
          <div style="font-weight: 700; font-size: 12px; color: #00f0ff;">${plateNumber}</div>
          <div style="font-size: 10px; color: #94a3b8;">${speed} km/h</div>
        `, { permanent: true, direction: 'top', offset: [0, -22] });

      vehicleMarkerRef.current = marker;
    } else {
      vehicleMarkerRef.current.setLatLng([latitude, longitude]);
      vehicleMarkerRef.current.setIcon(customIcon);
      vehicleMarkerRef.current.setTooltipContent(`
        <div style="font-weight: 700; font-size: 12px; color: #00f0ff;">${plateNumber}</div>
        <div style="font-size: 10px; color: #94a3b8;">${speed} km/h • H: ${Math.round(heading)}°</div>
      `);
    }
  }, [vehicleTelemetry]);

  return (
    <div
      ref={mapContainerRef}
      style={{
        width: '100vw',
        height: '100vh',
        position: 'absolute',
        top: 0,
        left: 0,
        zIndex: 1,
        cursor: clickMode ? 'crosshair' : 'grab',
      }}
    />
  );
}
