import { useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';
import type { OperationsSnapshot } from '../types/operations';
import { positionFreshness } from '../types/operations';
import type { VehicleType } from '../types/fleet';
import { vehicleTypeLabel } from '../types/fleet';
import { vehicleMarkerGlyph } from '../utils/vehiclePresentation';

/** A newly scheduled trip has no telemetry yet, but still has a meaningful map position. */
export interface VehicleMarkerAnchor {
  vehicleId: number;
  tripId: number;
  vehiclePlateNumber: string;
  vehicleType: VehicleType;
  latitude: number;
  longitude: number;
}

type MarkerPosition = OperationsSnapshot['positions'][number] | VehicleMarkerAnchor & { source: 'PLANNED' };

export function useVehicleMarkers({ mapRef, snapshot, plannedPositions = [], now, visible, selectedId, following, onSelect, onFocus, groupSelection = false }: {
  mapRef: RefObject<L.Map | null>; snapshot: OperationsSnapshot | null; now: number; visible: boolean;
  plannedPositions?: readonly VehicleMarkerAnchor[];
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
    const actualPositions = visible ? snapshot?.positions ?? [] : [];
    const plannedByVehicle = new Map<number, VehicleMarkerAnchor>();
    if (visible) plannedPositions.forEach(anchor => plannedByVehicle.set(anchor.vehicleId, anchor));
    const anchors = [...plannedByVehicle.values()].map(anchor => ({ ...anchor, source: 'PLANNED' as const }));
    // Keep one marker per vehicle. Until the new trip emits telemetry, its planned
    // start position takes precedence over an old position from a previous trip.
    const positions: MarkerPosition[] = [
      ...actualPositions.filter(point => !plannedByVehicle.has(point.vehicleId)
        || anchors.some(anchor => anchor.vehicleId === point.vehicleId && anchor.tripId === point.tripId)),
      ...anchors.filter(anchor => !actualPositions.some(point => point.vehicleId === anchor.vehicleId && point.tripId === anchor.tripId)),
    ];
    const ids = new Set(positions.map(point => point.vehicleId));
    markers.current.forEach((marker,id) => { if (!ids.has(id)) { marker.off(); marker.remove(); markers.current.delete(id); } });
    for (const point of positions) {
      if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) || Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) continue;
      const isPlanned = point.source === 'PLANNED';
      const trip = snapshot?.trips.find(trip => trip.id === point.tripId);
      const run = snapshot?.simulations.find(run => run.tripId === point.tripId);
      const freshness = isPlanned ? 'stale' : positionFreshness(point, now);
      const state = isPlanned ? 'Chưa khởi hành' : trip?.status === 'COMPLETED' || trip?.status === 'CANCELLED' ? 'Chuyến đã kết thúc' :
        run?.status === 'PAUSED' ? 'Tạm dừng' : freshness === 'offline' ? 'Mất tín hiệu' : freshness === 'stale' ? 'Vị trí cũ' : 'Đang cập nhật';
      const stationary = isPlanned || (run?.status !== undefined && run.status !== 'RUNNING');
      const stale = freshness !== 'fresh' || stationary;
      const vehicleType = trip?.vehicleType ?? (isPlanned ? point.vehicleType : 'CAR');
      const plateNumber = trip?.vehiclePlateNumber ?? (isPlanned ? point.vehiclePlateNumber : undefined);
      const heading = isPlanned ? 0 : point.heading;
      const icon = L.divIcon({
        className: 'live-vehicle-icon', iconSize: [44,44], iconAnchor: [22,22], tooltipAnchor: [0,-26],
        html: `<div class="live-vehicle-marker ${stale ? 'muted' : ''} ${isPlanned ? 'planned' : ''} ${point.vehicleId === selectedId ? 'selected' : ''}" data-vehicle-id="${Number(point.vehicleId)}" data-vehicle-type="${vehicleType}" style="--heading:${Number.isFinite(heading) ? heading : 0}deg">${vehicleMarkerGlyph(vehicleType)}</div>`,
      });
      let marker = markers.current.get(point.vehicleId);
      if (!marker) {
        marker = L.marker([point.latitude,point.longitude], { icon, keyboard: true, title: `Xe ${plateNumber ?? point.vehicleId}` }).addTo(map);
        markers.current.set(point.vehicleId,marker);
      } else if (marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker')?.dataset.vehicleType !== vehicleType) {
        marker.setIcon(icon);
      }
      const previous = marker.getLatLng();
      if (previous.lat !== point.latitude || previous.lng !== point.longitude) marker.setLatLng([point.latitude,point.longitude]);
      marker.setZIndexOffset(point.vehicleId === selectedId ? 1000 : 800);
      const body = marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker');
      if (body) {
        body.classList.toggle('muted',stale); body.classList.toggle('selected',point.vehicleId === selectedId);
        body.classList.toggle('planned', isPlanned);
        body.style.setProperty('--heading', `${Number.isFinite(heading) ? heading : 0}deg`);
      }
      const text = document.createElement('span');
      const sourceLabel = isPlanned ? 'KẾ HOẠCH' : point.source === 'SIMULATOR' ? 'GIẢ LẬP' : 'GPS';
      const speedLabel = stationary ? '0' : point.speedKmh.toFixed(1);
      const timeLabel = isPlanned ? 'chưa khởi hành' : new Date(point.recordedAt).toLocaleTimeString('vi-VN');
      text.textContent = `${plateNumber ?? point.vehicleId} · ${vehicleTypeLabel(vehicleType)} · ${sourceLabel} · ${state} · ${speedLabel} km/h · ${timeLabel}`;
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
  }, [mapRef,snapshot,plannedPositions,now,visible,selectedId,following,onSelect,onFocus,groupSelection]);
}
