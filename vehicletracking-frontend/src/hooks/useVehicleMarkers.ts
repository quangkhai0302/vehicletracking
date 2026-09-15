import { useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';
import type { OperationsSnapshot } from '../types/operations';
import { positionFreshness } from '../types/operations';

// Top-down car: its front points north at heading 0, matching telemetry bearings.
const vehicleGlyph = `<svg class="live-vehicle-glyph" viewBox="0 0 32 40" aria-hidden="true" focusable="false">
  <path d="m13 4 3-3 3 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <rect x="5" y="13" width="4" height="7" rx="1.5" fill="#0f172a"/>
  <rect x="23" y="13" width="4" height="7" rx="1.5" fill="#0f172a"/>
  <rect x="5" y="27" width="4" height="6" rx="1.5" fill="#0f172a"/>
  <rect x="23" y="27" width="4" height="6" rx="1.5" fill="#0f172a"/>
  <rect x="8" y="7" width="16" height="30" rx="6" fill="currentColor" stroke="#fff" stroke-width="1.5"/>
  <path d="m11 14 1 6h8l1-6c-3-2-7-2-10 0Z" fill="#0c4a6e"/>
  <path d="m12 29-1 4h10l-1-4Z" fill="#0c4a6e"/>
  <path d="M11 10h2m6 0h2" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
  <path d="M11 35h2m6 0h2" stroke="#fb7185" stroke-width="2" stroke-linecap="round"/>
</svg>`;

export function useVehicleMarkers({ mapRef, snapshot, now, visible, selectedId, following, onSelect, onFocus, groupSelection = false }: {
  mapRef: RefObject<L.Map | null>; snapshot: OperationsSnapshot | null; now: number; visible: boolean;
  selectedId: number | null; following: boolean; onSelect: (id: number) => void;
  onFocus: (point: L.LatLngExpression, zoom?: number) => void;
  groupSelection?: boolean;
}) {
  const markers = useRef(new Map<number, L.Marker>());
  const followedPoint = useRef<string | null>(null);
  const vehiclePicker = useRef<L.Popup | null>(null);
  useEffect(() => () => { vehiclePicker.current?.remove(); vehiclePicker.current=null; }, [groupSelection]);
  useEffect(() => {
    const map = mapRef.current;
    const current = markers.current;
    return () => { current.forEach(marker => { marker.off(); if (map?.hasLayer(marker)) marker.remove(); }); current.clear(); };
  }, [mapRef]);
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const positions = visible ? snapshot?.positions ?? [] : [];
    const ids = new Set(positions.map(point => point.vehicleId));
    markers.current.forEach((marker,id) => { if (!ids.has(id)) { marker.off(); marker.remove(); markers.current.delete(id); } });
    for (const point of positions) {
      if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) || Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) continue;
      const trip = snapshot?.trips.find(trip => trip.id === point.tripId);
      const run = snapshot?.simulations.find(run => run.tripId === point.tripId);
      const freshness = positionFreshness(point, now);
      const state = trip?.status === 'COMPLETED' || trip?.status === 'CANCELLED' ? 'Chuyến đã kết thúc' :
        run?.status === 'PAUSED' ? 'Tạm dừng' : freshness === 'offline' ? 'Mất tín hiệu' : freshness === 'stale' ? 'Vị trí cũ' : 'Đang cập nhật';
      const stationary = run?.status !== undefined && run.status !== 'RUNNING';
      const stale = freshness !== 'fresh' || stationary;
      const icon = L.divIcon({
        className: 'live-vehicle-icon', iconSize: [44,44], iconAnchor: [22,22], tooltipAnchor: [0,-26],
        html: `<div class="live-vehicle-marker ${stale ? 'muted' : ''} ${point.vehicleId === selectedId ? 'selected' : ''}" data-vehicle-id="${Number(point.vehicleId)}" style="--heading:${Number.isFinite(point.heading) ? point.heading : 0}deg">${vehicleGlyph}</div>`,
      });
      let marker = markers.current.get(point.vehicleId);
      if (!marker) {
        marker = L.marker([point.latitude,point.longitude], { icon, keyboard: true, title: `Xe ${trip?.vehiclePlateNumber ?? point.vehicleId}` }).addTo(map);
        markers.current.set(point.vehicleId,marker);
      }
      const previous = marker.getLatLng();
      if (previous.lat !== point.latitude || previous.lng !== point.longitude) marker.setLatLng([point.latitude,point.longitude]);
      marker.setZIndexOffset(point.vehicleId === selectedId ? 1000 : 800);
      const body = marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker');
      if (body) {
        body.classList.toggle('muted',stale); body.classList.toggle('selected',point.vehicleId === selectedId);
        body.style.setProperty('--heading', `${Number.isFinite(point.heading) ? point.heading : 0}deg`);
      }
      const text = document.createElement('span');
      text.textContent = `${trip?.vehiclePlateNumber ?? point.vehicleId} · ${point.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS'} · ${state} · ${stationary ? 0 : point.speedKmh.toFixed(1)} km/h · ${new Date(point.recordedAt).toLocaleTimeString('vi-VN')}`;
      if (marker.getTooltip()) marker.setTooltipContent(text); else marker.bindTooltip(text, { direction: 'top', opacity: .95 });
      marker.off('click').on('click', () => {
        const anchor = map.latLngToContainerPoint([point.latitude,point.longitude]);
        const nearby = groupSelection ? positions.filter(candidate => candidate.source==='SIMULATOR'
          && map.latLngToContainerPoint([candidate.latitude,candidate.longitude]).distanceTo(anchor)<36) : [];
        if (nearby.length > 1) {
          const content = document.createElement('div'); content.className='simulation-station-picker';
          const title = document.createElement('strong'); title.textContent=`${nearby.length} xe gần vị trí này`; content.append(title);
          for (const candidate of nearby) {
            const button=document.createElement('button');button.type='button';button.dataset.simulationVehicle=String(candidate.vehicleId);
            button.textContent=snapshot?.trips.find(item=>item.id===candidate.tripId)?.vehiclePlateNumber ?? `Xe ${candidate.vehicleId}`;
            button.onclick=()=>{map.closePopup();onSelect(candidate.vehicleId);onFocus([candidate.latitude,candidate.longitude],16);};content.append(button);
          }
          const popup=L.popup({maxWidth:260}).setLatLng([point.latitude,point.longitude]).setContent(content).openOn(map);
          vehiclePicker.current=popup;
          popup.once('remove',()=>content.querySelectorAll('button').forEach(button=>{button.onclick=null;}));
        } else { onSelect(point.vehicleId); onFocus([point.latitude,point.longitude],16); }
      });
    }
    const selected = positions.find(point => point.vehicleId === selectedId);
    const key = following && selected ? `${selected.vehicleId}:${selected.latitude}:${selected.longitude}` : null;
    if (key && key !== followedPoint.current && selected) onFocus([selected.latitude,selected.longitude]);
    followedPoint.current = key;
  }, [mapRef,snapshot,now,visible,selectedId,following,onSelect,onFocus,groupSelection]);
}
