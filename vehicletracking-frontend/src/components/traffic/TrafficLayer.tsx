import { useEffect, type RefObject } from 'react';
import L from 'leaflet';
import type { TrafficIncident } from '../../types/map';
import type { TrafficIncidentsResponse } from '../../types/traffic';
import './traffic.css';

function createTrafficIncidentIcon(incident: TrafficIncident): L.DivIcon {
  const typeLower = (incident.type || '').toLowerCase();
  const descLower = (incident.description || '').toLowerCase();

  let iconSvg = '';

  if (typeLower.includes('closure') || typeLower.includes('blocked') || descLower.includes('đóng đường') || descLower.includes('cấm')) {
    // Google Maps iconic No Entry / Road Closed circle (⛔)
    iconSvg = `
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
        <circle cx="12" cy="12" r="10" fill="#d93025" stroke="#ffffff" stroke-width="2" />
        <rect x="6" y="10.5" width="12" height="3" rx="1.5" fill="#ffffff" />
      </svg>
    `;
  } else if (typeLower.includes('accident') || descLower.includes('tai nạn') || incident.criticality === 'critical') {
    // Google Maps Warning Accident Triangle
    iconSvg = `
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
        <path d="M12 2L1 21h22L12 2z" fill="#d93025" stroke="#ffffff" stroke-width="1.8" stroke-linejoin="round" />
        <rect x="11" y="8" width="2" height="6" rx="1" fill="#ffffff" />
        <circle cx="12" cy="17" r="1.2" fill="#ffffff" />
      </svg>
    `;
  } else if (typeLower.includes('construction') || typeLower.includes('work') || descLower.includes('thi công') || descLower.includes('sửa')) {
    // Google Maps Construction icon
    iconSvg = `
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
        <circle cx="12" cy="12" r="10" fill="#f57c00" stroke="#ffffff" stroke-width="2" />
        <path d="M7 16h10l-5-9-5 9zm3.5-2l1.5-2.7 1.5 2.7h-3z" fill="#ffffff" />
      </svg>
    `;
  } else {
    // General traffic incident
    iconSvg = `
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none">
        <circle cx="12" cy="12" r="10" fill="#f9a825" stroke="#ffffff" stroke-width="2" />
        <rect x="11" y="7" width="2" height="7" rx="1" fill="#202124" />
        <circle cx="12" cy="17" r="1.2" fill="#202124" />
      </svg>
    `;
  }

  return L.divIcon({
    className: 'gm-incident-div-icon',
    html: `<div class="gm-traffic-incident-badge">${iconSvg}</div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
    popupAnchor: [0, -12],
    tooltipAnchor: [0, -12],
  });
}

function createIncidentPopup(incident: TrafficIncident): HTMLDivElement {
  const crit = incident.criticality || 'minor';
  const critLabels: Record<string, string> = {
    critical: 'Nghiêm trọng',
    major: 'Đáng chú ý',
    minor: 'Nhẹ',
    low: 'Bình thường',
  };
  const critLabel = critLabels[crit] || crit;
  const typeText = incident.type ? incident.type.toUpperCase() : 'SỰ CỐ GIAO THÔNG';

  const card = document.createElement('div');
  card.className = 'gm-incident-card';
  const top = document.createElement('div');
  top.className = 'gm-incident-card-top';
  const type = document.createElement('span');
  type.className = 'gm-incident-type-label';
  type.textContent = typeText;
  const badge = document.createElement('span');
  badge.className = `gm-incident-badge ${crit}`;
  badge.textContent = critLabel;
  top.append(type, badge);
  const description = document.createElement('div');
  description.className = 'gm-incident-desc';
  description.textContent = incident.description || 'Không có thông tin chi tiết.';
  card.append(top, description);
  return card;
}

export function TrafficLayer({
  mapRef,
  mapReady,
  visible,
  incidents,
}: {
  mapRef: RefObject<L.Map | null>;
  mapReady: boolean;
  visible: boolean;
  incidents: TrafficIncidentsResponse | null;
}) {



  useEffect(() => {
    const map = mapRef.current;
    if (!mapReady || !map || !visible) return;

    if (!map.getPane('trafficPane')) {
      const pane = map.createPane('trafficPane');
      pane.style.zIndex = '350';
    }

    const layer = L.layerGroup().addTo(map);

    incidents?.results.forEach((incident) => {
      const position =
        incident.center && incident.center.length >= 2
          ? incident.center
          : incident.points && incident.points.length > 0
          ? incident.points[0]
          : null;

      if (!position || position.length < 2) return;

      const icon = createTrafficIncidentIcon(incident);
      const marker = L.marker(position as L.LatLngExpression, {
        icon,
        keyboard: false,
        zIndexOffset: 1000,
      });

      marker.bindPopup(createIncidentPopup(incident), {
        className: 'gm-incident-popup-wrapper',
        maxWidth: 280,
      });
      const label = document.createElement('span');
      label.textContent = incident.description || 'Sự cố giao thông';
      marker.bindTooltip(label, { direction: 'top', offset: [0, -12] });

      marker.addTo(layer);
    });

    return () => {
      layer.clearLayers();
      map.removeLayer(layer);
    };
  }, [incidents, mapReady, mapRef, visible]);

  return null;
}
