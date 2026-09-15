import { useCallback, useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';
import type { OperationsSnapshot } from '../types/operations';
import { positionFreshness } from '../types/operations';
import type { VehicleType } from '../types/fleet';
import { vehicleTypeLabel } from '../types/fleet';
import { vehicleMarkerGlyph } from '../utils/vehiclePresentation';
import { PLAYBACK_DELAY_MS, pointOnMotionPath, sampleMotion, type MotionPath, type MotionSample } from '../utils/vehicleMotion';

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
type VehicleAnimation = {
  samples: MotionSample[];
  identity: string;
  eventId: string;
  path?: MotionPath;
};

export function useVehicleMarkers({ mapRef, snapshot, plannedPositions = [], motionPaths, now, visible, selectedId, following, onSelect, onFocus, groupSelection = false }: {
  mapRef: RefObject<L.Map | null>; snapshot: OperationsSnapshot | null; now: number; visible: boolean;
  plannedPositions?: readonly VehicleMarkerAnchor[];
  selectedId: number | null; following: boolean; onSelect: (id: number) => void;
  onFocus: (point: L.LatLngExpression, zoom?: number) => void;
  groupSelection?: boolean;
  motionPaths?: ReadonlyMap<number, MotionPath>;
}) {
  const markers = useRef(new Map<number, L.Marker>());
  const markerTripIds = useRef(new Map<number, number>());
  const animations = useRef(new Map<number, VehicleAnimation>());
  const animationFrame = useRef<number | null>(null);
  const followedPoint = useRef<string | null>(null);
  const followingRef = useRef({ following, selectedId, onFocus });
  useEffect(() => { followingRef.current = { following, selectedId, onFocus }; }, [following, selectedId, onFocus]);
  const vehiclePicker = useRef<L.Popup | null>(null);
  useEffect(() => () => { vehiclePicker.current?.remove(); vehiclePicker.current=null; }, [groupSelection]);
  const cancelAnimations = useCallback(() => {
    if (animationFrame.current !== null && typeof window !== 'undefined') window.cancelAnimationFrame(animationFrame.current);
    animationFrame.current = null;
    animations.current.clear();
    markerTripIds.current.clear();
  }, []);
  const scheduleAnimations = useCallback(() => {
    if (animationFrame.current !== null || typeof window === 'undefined') return;
    const tick = (timestamp: number) => {
      let active = false;
      animations.current.forEach((animation, vehicleId) => {
        const marker = markers.current.get(vehicleId);
        if (!marker) {
          animations.current.delete(vehicleId);
          return;
        }
        const renderAt = timestamp - PLAYBACK_DELAY_MS;
        const point = sampleMotion(animation.samples, renderAt, animation.path);
        if (!point) return;
        marker.setLatLng([point.latitude, point.longitude]);
        marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker')
          ?.style.setProperty('--heading', `${point.heading}deg`);
        const follow = followingRef.current;
        if (follow.following && follow.selectedId === vehicleId) follow.onFocus([point.latitude, point.longitude]);
        while (animation.samples.length > 2 && animation.samples[1].time < renderAt) animation.samples.shift();
        if (renderAt < animation.samples[animation.samples.length - 1].time) active = true;
      });
      animationFrame.current = active ? window.requestAnimationFrame(tick) : null;
    };
    animationFrame.current = window.requestAnimationFrame(tick);
  }, []);
  useEffect(() => {
    const map = mapRef.current;
    const current = markers.current;
    return () => {
      cancelAnimations();
      current.forEach(marker => { marker.off(); if (map?.hasLayer(marker)) marker.remove(); });
      current.clear();
    };
  }, [cancelAnimations, mapRef]);
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
    markers.current.forEach((marker,id) => {
      if (!ids.has(id)) {
        marker.off(); marker.remove(); markers.current.delete(id);
        markerTripIds.current.delete(id); animations.current.delete(id);
      }
    });
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
      const target = L.latLng(point.latitude, point.longitude);
      const currentAnimation = animations.current.get(point.vehicleId);
      const identity = `${point.tripId}:${isPlanned ? 'planned' : point.attemptNumber ?? run?.attemptNumber ?? 1}:${run?.routeRevisionId ?? 0}`;
      const sample: MotionSample = { time: performance.now(), latitude: point.latitude, longitude: point.longitude,
        heading, progress: !isPlanned && point.source === 'SIMULATOR' ? run?.frame?.progressPercent : undefined };
      const path = motionPaths?.get(point.tripId);
      const projected = path && sample.progress !== undefined ? pointOnMotionPath(path, sample.progress) : null;
      if (!projected || map.distance(target, projected) > 30) sample.progress = undefined;
      if (stationary || freshness !== 'fresh') {
        animations.current.delete(point.vehicleId);
        marker.setLatLng(target);
      } else if (!currentAnimation || currentAnimation.identity !== identity || map.distance(previous, target) > 5000) {
        marker.setLatLng(target);
        animations.current.set(point.vehicleId, { identity, samples: [sample], eventId: point.eventId,
          path: motionPaths?.get(point.tripId) });
      } else if (currentAnimation.eventId !== point.eventId) {
        // Keep the two sides of each interpolation across snapshot arrivals.
        // Equal/duplicate snapshots never restart or shorten the animation.
        currentAnimation.eventId = point.eventId;
        currentAnimation.samples.push(sample);
        if (currentAnimation.samples.length > 12) currentAnimation.samples.splice(0, currentAnimation.samples.length - 12);
        currentAnimation.path = motionPaths?.get(point.tripId);
        scheduleAnimations();
      }
      markerTripIds.current.set(point.vehicleId, point.tripId);
      marker.setZIndexOffset(point.vehicleId === selectedId ? 1000 : 800);
      const body = marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker');
      if (body) {
        body.classList.toggle('muted',stale); body.classList.toggle('selected',point.vehicleId === selectedId);
        body.classList.toggle('planned', isPlanned);
        if (!animations.current.has(point.vehicleId)) body.style.setProperty('--heading', `${Number.isFinite(heading) ? heading : 0}deg`);
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
    if (key && key !== followedPoint.current && selected && !animations.current.has(selected.vehicleId)) onFocus([selected.latitude,selected.longitude]);
    followedPoint.current = key;
  }, [mapRef,snapshot,plannedPositions,motionPaths,now,visible,selectedId,following,onSelect,onFocus,groupSelection,scheduleAnimations]);
}
