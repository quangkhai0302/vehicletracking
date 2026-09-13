import { useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';
import type { OperationsSnapshot } from '../types/operations';
import { positionFreshness } from '../types/operations';

export function useVehicleMarkers({ mapRef, snapshot, now, visible, selectedId, following, onSelect, onFocus }: {
  mapRef: RefObject<L.Map | null>; snapshot: OperationsSnapshot | null; now: number; visible: boolean;
  selectedId: number | null; following: boolean; onSelect: (id: number) => void;
  onFocus: (point: L.LatLngExpression, zoom?: number) => void;
}) {
  const markers = useRef(new Map<number, L.Marker>());
  const followedPoint = useRef<string | null>(null);
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
        className: 'live-vehicle-icon', iconSize: [36,36], iconAnchor: [18,18], tooltipAnchor: [0,-22],
        html: `<div class="live-vehicle-marker ${stale ? 'muted' : ''} ${point.vehicleId === selectedId ? 'selected' : ''}" data-vehicle-id="${Number(point.vehicleId)}" style="--heading:${Number.isFinite(point.heading) ? point.heading : 0}deg"><span>▲</span></div>`,
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
      marker.off('click').on('click', () => { onSelect(point.vehicleId); onFocus([point.latitude,point.longitude],16); });
    }
    const selected = positions.find(point => point.vehicleId === selectedId);
    const key = following && selected ? `${selected.vehicleId}:${selected.latitude}:${selected.longitude}` : null;
    if (key && key !== followedPoint.current && selected) onFocus([selected.latitude,selected.longitude]);
    followedPoint.current = key;
  }, [mapRef,snapshot,now,visible,selectedId,following,onSelect,onFocus]);
}
