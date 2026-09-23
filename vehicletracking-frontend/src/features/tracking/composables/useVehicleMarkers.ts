import { onScopeDispose, toValue, watch, watchEffect, type MaybeRefOrGetter, type ShallowRef } from 'vue';
import L from 'leaflet';
import type { OperationsSnapshot } from '@/features/tracking/types/operations';
import { positionFreshness } from '@/features/tracking/types/operations';
import type { VehicleType } from '@/features/fleet/types/fleet';
import { vehicleMarkerGlyph } from '@/features/fleet/utils/vehiclePresentation';
import { PLAYBACK_DELAY_MS, pointOnMotionPath, sampleMotion, type MotionPath, type MotionSample } from '@/features/fleet/utils/vehicleMotion';

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

export function useVehicleMarkers(options: MaybeRefOrGetter<{
  mapRef: ShallowRef<L.Map | null>; snapshot: OperationsSnapshot | null; now: number; visible: boolean;
  plannedPositions?: readonly VehicleMarkerAnchor[];
  selectedId: number | null; following: boolean; onSelect: (id: number, tripId: number) => void;
  onFocus: (point: L.LatLngExpression, zoom?: number) => void;
  groupSelection?: boolean; motionPaths?: ReadonlyMap<number, MotionPath>;
}>) {
  const markers = new Map<number, L.Marker>();
  const markerTripIds = new Map<number, number>();
  const animations = new Map<number, VehicleAnimation>();
  let animationFrame: number | null = null;
  let followedPoint: string | null = null;
  let vehiclePicker: L.Popup | null = null;
  const clearPicker = () => { vehiclePicker?.remove(); vehiclePicker = null; };
  watch(() => toValue(options).groupSelection, clearPicker);
  onScopeDispose(clearPicker);
  const cancelAnimations = () => {
    if (animationFrame !== null && typeof window !== 'undefined') window.cancelAnimationFrame(animationFrame);
    animationFrame = null;
    animations.clear();
    markerTripIds.clear();
  };
  const scheduleAnimations = () => {
    if (animationFrame !== null || typeof window === 'undefined') return;
    const tick = (timestamp: number) => {
      let active = false;
      animations.forEach((animation, vehicleId) => {
        const marker = markers.get(vehicleId);
        if (!marker) {
          animations.delete(vehicleId);
          return;
        }
        const renderAt = timestamp - PLAYBACK_DELAY_MS;
        const point = sampleMotion(animation.samples, renderAt, animation.path);
        if (!point) return;
        marker.setLatLng([point.latitude, point.longitude]);
        marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker')
          ?.style.setProperty('--heading', `${point.heading}deg`);
        const follow = toValue(options);
        if (follow.following && follow.selectedId === vehicleId) follow.onFocus([point.latitude, point.longitude]);
        while (animation.samples.length > 2 && animation.samples[1].time < renderAt) animation.samples.shift();
        if (renderAt < animation.samples[animation.samples.length - 1].time) active = true;
      });
      animationFrame = active ? window.requestAnimationFrame(tick) : null;
    };
    animationFrame = window.requestAnimationFrame(tick);
  };
  watch(() => toValue(options).mapRef.value, (map, _previous, cleanup) => {
    cleanup(() => {
      clearPicker(); cancelAnimations();
      markers.forEach(marker => { marker.off(); if (map?.hasLayer(marker)) marker.remove(); });
      markers.clear();
    });
  }, { immediate: true, flush: 'sync' });
  watchEffect(() => {
    const { mapRef, snapshot, plannedPositions = [], motionPaths, now, visible, selectedId,
      following, onSelect, onFocus, groupSelection = false } = toValue(options);
    const map = mapRef.value;
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
    markers.forEach((marker,id) => {
      if (!ids.has(id)) {
        marker.off(); marker.remove(); markers.delete(id);
        markerTripIds.delete(id); animations.delete(id);
      }
    });
    for (const point of positions) {
      if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) || Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180) continue;
      const isPlanned = point.source === 'PLANNED';
      const trip = snapshot?.trips.find(trip => trip.id === point.tripId);
      const run = snapshot?.simulations.find(run => run.tripId === point.tripId);
      const freshness = isPlanned ? 'stale' : positionFreshness(point, now);
      const stationary = isPlanned || (run?.status !== undefined && run.status !== 'RUNNING');
      const stale = freshness !== 'fresh' || stationary;
      const vehicleType = trip?.vehicleType ?? (isPlanned ? point.vehicleType : 'CAR');
      const plateNumber = trip?.vehiclePlateNumber ?? (isPlanned ? point.vehiclePlateNumber : undefined);
      const heading = isPlanned ? 0 : point.heading;
      const icon = L.divIcon({
        className: 'live-vehicle-icon', iconSize: [44,44], iconAnchor: [22,22], tooltipAnchor: [0,-26],
        html: `<div class="live-vehicle-marker ${stale ? 'muted' : ''} ${isPlanned ? 'planned' : ''} ${point.vehicleId === selectedId ? 'selected' : ''}" data-vehicle-id="${Number(point.vehicleId)}" data-vehicle-type="${vehicleType}" style="--heading:${Number.isFinite(heading) ? heading : 0}deg">${vehicleMarkerGlyph(vehicleType)}</div>`,
      });
      let marker = markers.get(point.vehicleId);
      if (!marker) {
        marker = L.marker([point.latitude,point.longitude], { icon, keyboard: true, title: `Xe ${plateNumber ?? point.vehicleId}` }).addTo(map);
        markers.set(point.vehicleId,marker);
      } else if (marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker')?.dataset.vehicleType !== vehicleType) {
        marker.setIcon(icon);
      }
      const previous = marker.getLatLng();
      const target = L.latLng(point.latitude, point.longitude);
      const currentAnimation = animations.get(point.vehicleId);
      const identity = `${point.tripId}:${isPlanned ? 'planned' : point.attemptNumber ?? run?.attemptNumber ?? 1}:${run?.routeRevisionId ?? 0}`;
      const receivedAt = performance.now();
      const sample: MotionSample = { time: receivedAt, latitude: point.latitude, longitude: point.longitude,
        heading, progress: !isPlanned && point.source === 'SIMULATOR' ? run?.frame?.progressPercent : undefined };
      const path = motionPaths?.get(point.tripId);
      const projected = path && sample.progress !== undefined ? pointOnMotionPath(path, sample.progress) : null;
      if (!projected || map.distance(target, projected) > 30) sample.progress = undefined;
      if (stationary || freshness !== 'fresh') {
        animations.delete(point.vehicleId);
        marker.setLatLng(target);
      } else if (!currentAnimation || currentAnimation.identity !== identity || map.distance(previous, target) > 5000) {
        marker.setLatLng(target);
        animations.set(point.vehicleId, { identity, samples: [sample], eventId: point.eventId,
          path: motionPaths?.get(point.tripId) });
      } else if (currentAnimation.eventId !== point.eventId) {
        // Keep the two sides of each interpolation across snapshot arrivals.
        // Equal/duplicate snapshots never restart or shorten the animation.
        currentAnimation.eventId = point.eventId;
        const renderAt = receivedAt - PLAYBACK_DELAY_MS;
        const lastSample = currentAnimation.samples[currentAnimation.samples.length - 1];
        // If snapshot generation or network delivery took longer than the
        // presentation buffer, renderAt has already passed the old tail. The
        // old implementation then jumped straight into the new point. Create
        // a sample at the currently displayed position and ease to the new
        // point over the elapsed gap instead. This keeps delayed deployments
        // smooth without making a disconnected vehicle move forever.
        if (lastSample && (renderAt > lastSample.time || receivedAt <= lastSample.time)) {
          const displayed = sampleMotion(currentAnimation.samples, renderAt, currentAnimation.path) ?? {
            time: renderAt, latitude: previous.lat, longitude: previous.lng, heading,
          };
          const gap = lastSample ? Math.abs(receivedAt - lastSample.time) : PLAYBACK_DELAY_MS;
          const duration = Math.max(PLAYBACK_DELAY_MS, Math.min(10_000, gap));
          currentAnimation.samples = [
            { ...displayed, time: renderAt },
            { ...sample, time: renderAt + duration },
          ];
        } else {
          currentAnimation.samples.push(sample);
        }
        if (currentAnimation.samples.length > 12) currentAnimation.samples.splice(0, currentAnimation.samples.length - 12);
        currentAnimation.path = motionPaths?.get(point.tripId);
        scheduleAnimations();
      }
      markerTripIds.set(point.vehicleId, point.tripId);
      marker.setZIndexOffset(point.vehicleId === selectedId ? 1000 : 800);
      const body = marker.getElement()?.querySelector<HTMLElement>('.live-vehicle-marker');
      if (body) {
        body.classList.toggle('muted',stale); body.classList.toggle('selected',point.vehicleId === selectedId);
        body.classList.toggle('planned', isPlanned);
        if (!animations.has(point.vehicleId)) body.style.setProperty('--heading', `${Number.isFinite(heading) ? heading : 0}deg`);
      }
      const text = document.createElement('span');
      const statusLabel = isPlanned ? 'Chưa khởi hành'
        : trip?.status === 'COMPLETED' ? 'Đã kết thúc'
        : trip?.status === 'CANCELLED' ? 'Đã hủy'
        : run?.status === 'PAUSED' ? 'Tạm dừng'
        : freshness === 'offline' ? 'Mất tín hiệu'
        : freshness === 'stale' ? 'Vị trí cũ'
        : `${point.speedKmh.toFixed(1)} km/h`;
      text.textContent = `${plateNumber ?? point.vehicleId} · ${statusLabel}`;
      if (marker.getTooltip()) marker.setTooltipContent(text); else marker.bindTooltip(text, { direction: 'top', opacity: .95 });
      marker.off('click').on('click', () => {
        const displayed = marker.getLatLng();
        const anchor = map.latLngToContainerPoint(displayed);
        const nearby = groupSelection ? positions.filter(candidate => candidate.source==='SIMULATOR'
          && map.latLngToContainerPoint(markers.get(candidate.vehicleId)?.getLatLng()
            ?? [candidate.latitude,candidate.longitude]).distanceTo(anchor)<36) : [];
        if (nearby.length > 1) {
          const content = document.createElement('div'); content.className='simulation-station-picker';
          const title = document.createElement('strong'); title.textContent=`${nearby.length} xe gần vị trí này`; content.append(title);
          for (const candidate of nearby) {
            const button=document.createElement('button');button.type='button';button.dataset.simulationVehicle=String(candidate.vehicleId);
            button.textContent=snapshot?.trips.find(item=>item.id===candidate.tripId)?.vehiclePlateNumber ?? `Xe ${candidate.vehicleId}`;
            button.onclick=()=>{map.closePopup();onSelect(candidate.vehicleId, candidate.tripId);onFocus(markers.get(candidate.vehicleId)?.getLatLng() ?? [candidate.latitude,candidate.longitude],16);};content.append(button);
          }
          const popup=L.popup({maxWidth:260}).setLatLng(displayed).setContent(content).openOn(map);
          vehiclePicker=popup;
          popup.once('remove',()=>content.querySelectorAll('button').forEach(button=>{button.onclick=null;}));
        } else { onSelect(point.vehicleId, point.tripId); onFocus(displayed,16); }
      });
    }
    const selected = positions.find(point => point.vehicleId === selectedId);
    const key = following && selected ? `${selected.vehicleId}:${selected.latitude}:${selected.longitude}` : null;
    if (key && key !== followedPoint && selected && !animations.has(selected.vehicleId)) onFocus([selected.latitude,selected.longitude]);
    followedPoint = key;
  });
}

