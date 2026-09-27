import { onMounted, onUnmounted, shallowRef, toValue, watch, type MaybeRefOrGetter, type ShallowRef } from 'vue';
import L from 'leaflet';
import { decodeFlexiblePolyline } from '@/features/map/utils/polyline';
import type { MapTheme } from '@/features/map/types/map';
import type { RouteDetail, RouteDraftStop, RouteStopRole } from '@/features/routes/types/route';
import type { WorkspaceMode } from '@/shared/types/workspace';
import type { useStationWorkspace } from '@/features/stations/composables/useStationWorkspace';

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

function createRouteStopIcon(sequenceNumber: number, role: RouteStopRole): L.DivIcon {
  const roleClass = role.toLowerCase();
  return L.divIcon({
    className: 'route-stop-div-icon',
    html: `<div class="route-stop-map-marker ${roleClass}" aria-hidden="true">
      <svg class="route-stop-map-marker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round" focusable="false">
        <path d="M4 6 2 7" />
        <path d="M10 6h4" />
        <path d="m22 7-2-1" />
        <rect width="16" height="16" x="4" y="3" rx="2" />
        <path d="M4 11h16" />
        <path d="M8 15h.01" />
        <path d="M16 15h.01" />
        <path d="M6 19v2" />
        <path d="M18 21v-2" />
      </svg>
      <span class="route-stop-map-marker-sequence">${sequenceNumber}</span>
    </div>`,
    iconSize: [42, 48],
    iconAnchor: [21, 46],
    popupAnchor: [0, -42],
    tooltipAnchor: [0, -40],
  });
}

function createStationIcon(state: 'default' | 'selected' | 'muted' | 'draft'): L.DivIcon {
  return L.divIcon({
    className: 'station-div-icon',
    html: `<span class="station-map-marker ${state}" aria-hidden="true">
      <svg class="station-map-marker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round" focusable="false">
        <path d="M4 6 2 7" />
        <path d="M10 6h4" />
        <path d="m22 7-2-1" />
        <rect width="16" height="16" x="4" y="3" rx="2" />
        <path d="M4 11h16" />
        <path d="M8 15h.01" />
        <path d="M16 15h.01" />
        <path d="M6 19v2" />
        <path d="M18 21v-2" />
      </svg>
      <span class="station-map-marker-state"></span>
    </span>`,
    iconSize: [40, 46],
    iconAnchor: [20, 44],
    popupAnchor: [0, -40],
    tooltipAnchor: [0, -38],
  });
}

function validCoordinate(value: string, min: number, max: number): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
}


interface MapLayerOptions {
  workspace: WorkspaceMode; showStations: boolean; showRoutes: boolean; showTraffic: boolean; theme: MapTheme;
  plannedRoute: RouteDetail | null; hasSimulationRoute: boolean; draftStops: RouteDraftStop[]; selectedDraftStopId: string | null;
  stationWorkspace: ReturnType<typeof useStationWorkspace>;
  setSelectedDraftStopId: (id: string) => void; setDrawerOpen: (open: boolean) => void; setActivePanel: (panel: 'context' | null) => void;
  setFollowingVehicle: (value: boolean) => void; releaseFocus: () => void; setToast: (message: string) => void;
  focusLocation: (point: L.LatLngExpression, zoom?: number) => void; fitBounds: (bounds: L.LatLngBounds) => void;
}
export function useMapLayers(mapContainerRef: ShallowRef<HTMLDivElement | null>, mapInstanceRef: ShallowRef<L.Map | null>, options: MaybeRefOrGetter<MapLayerOptions>) {
  let routeDraftLayer: L.LayerGroup | null = null, stationLayer: L.LayerGroup | null = null, draftLayer: L.LayerGroup | null = null;
  let plannedRouteLayer: L.LayerGroup | null = null, tileLayer: L.TileLayer | null = null;
  const plannedRouteBounds = shallowRef<L.LatLngBounds | null>(null);
  const basemapStatus = shallowRef<'loading' | 'ready' | 'error'>('loading');
  const basemapRetry = shallowRef(0);
  const retryBasemap = () => { basemapRetry.value += 1; };
  const stationMarkers = new Map<number, L.Marker>();
  const o = () => toValue(options);
  const s = () => o().stationWorkspace;
  const setStationForm: ReturnType<typeof useStationWorkspace>['setStationForm'] = value => s().setStationForm(value);
  const setPickingLocation = (value: boolean) => s().setPickingLocation(value);
  const setSelectedStationId = (value: number | null) => s().setSelectedStationId(value);
  const setStationError = (value: string | null) => s().setStationError(value);
  const setSelectedDraftStopId = (value: string) => o().setSelectedDraftStopId(value);
  const setDrawerOpen = (value: boolean) => o().setDrawerOpen(value);
  const setActivePanel = (value: 'context' | null) => o().setActivePanel(value);
  const setToast = (value: string) => o().setToast(value);
  const focusLocation = (point: L.LatLngExpression, zoom?: number) => o().focusLocation(point, zoom);
  const fitBounds = (bounds: L.LatLngBounds) => o().fitBounds(bounds);
  let disposeMap: (() => void) | undefined;
  onMounted(() => {
    const setFollowingVehicle = o().setFollowingVehicle, releaseFocus = o().releaseFocus;
    disposeMap = (() => {
    if (!mapContainerRef.value || mapInstanceRef.value) return;

    const map = L.map(mapContainerRef.value, {
      center: HCMC_CENTER,
      zoom: 13,
      zoomControl: false,
      preferCanvas: true,
      zoomSnap: 1,
      zoomDelta: 1,
      wheelPxPerZoomLevel: 120,
      inertia: true,
      inertiaDeceleration: 3400,
      inertiaMaxSpeed: 2000,
    });

    const trafficPane = map.createPane('trafficPane');
    trafficPane.style.zIndex = '350';

    const routePane = map.createPane('routePane');
    routePane.style.zIndex = '450';

    routeDraftLayer = L.layerGroup().addTo(map);

    plannedRouteLayer = L.layerGroup().addTo(map);
    stationLayer = L.layerGroup().addTo(map);
    draftLayer = L.layerGroup().addTo(map);

    // User drag starts -> Stop following vehicle
    const onDragStart = () => {
      setFollowingVehicle(false);
      releaseFocus();
    };

    map.on('dragstart', onDragStart);
    mapInstanceRef.value = map;


    return () => {
      map.off('dragstart', onDragStart);
      map.remove();
      mapInstanceRef.value = null;

      stationLayer = null;
      draftLayer = null;
      plannedRouteLayer = null;
      routeDraftLayer = null;
    };
    })();
  });
  // Child layer scopes dispose before the owning map instance is removed.
  onUnmounted(() => disposeMap?.());

  watch([mapInstanceRef, () => s().formMode, () => s().pickingLocation, () => o().workspace], (_value, _previous, cleanup) => {
    const { workspace } = o(); const { formMode, pickingLocation } = s();
    const dispose = (() => {
    const map = mapInstanceRef.value;
    const mapContainer = mapContainerRef.value;
    if (!map || workspace !== 'stations' || formMode === 'closed' || !pickingLocation) return;

    const onMapClick = (event: L.LeafletMouseEvent) => {
      setStationForm((current) => ({
        ...current,
        latitude: event.latlng.lat.toFixed(6),
        longitude: event.latlng.lng.toFixed(6),
      }));
      setPickingLocation(false);
      setDrawerOpen(true); setActivePanel('context');
      focusLocation(event.latlng);
    };

    map.on('click', onMapClick);
    mapContainer?.classList.add('station-picking');
    return () => {
      map.off('click', onMapClick);
      mapContainer?.classList.remove('station-picking');
    };
    })();
    cleanup(() => { dispose?.();  });
  }, { immediate: true });

  watch([mapInstanceRef, () => s().editingStation?.id, () => s().formMode, () => s().selectedStationId, () => s().stations, () => o().workspace, () => o().showStations, () => o().showRoutes, () => o().plannedRoute, () => o().draftStops], (_value, _previous, cleanup) => {
    const { workspace, showStations, showRoutes, plannedRoute, draftStops } = o(); const { formMode, editingStation, selectedStationId, stations } = s();
    (() => {
    const layer = stationLayer;
    const map = mapInstanceRef.value;
    if (!layer || !map) return;
    layer.clearLayers();
    stationMarkers.clear();

    if (!showStations) return;
    stations.forEach((station) => {
      if (showRoutes && (plannedRoute?.stops.some(stop => stop.stationId === station.id) ||
        (workspace === 'routes' && draftStops.some(stop => stop.stationId === station.id)))) return;
      if (workspace === 'stations' && formMode === 'edit' && editingStation?.id === station.id) return;
      const selected = workspace === 'stations' && selectedStationId === station.id;
      const position: L.LatLngExpression = [station.latitude, station.longitude];

      if (selected) {
        L.circle(position, {
          radius: station.checkinRadiusMeters,
          color: '#22d3ee',
          weight: 2,
          opacity: 0.85,
          fillColor: '#38bdf8',
          fillOpacity: 0.12,
          interactive: false,
        }).addTo(layer);
      }

      const marker = L.marker(position, {
        icon: createStationIcon(workspace === 'tracking' ? 'muted' : selected ? 'selected' : 'default'),
        zIndexOffset: selected ? 500 : 100,
        bubblingMouseEvents: false,
      });

      // H-02: Use DOM node and textContent to prevent XSS in Leaflet tooltip
      const stationTooltip = document.createElement('span');
      stationTooltip.textContent = station.name;
      marker.bindTooltip(stationTooltip, { direction: 'top', offset: [0, -38], opacity: 0.9 });

      if (workspace === 'tracking') {
        marker.on('click', () => {
          focusLocation([station.latitude, station.longitude], 16);
        });
      } else {
        marker.on('click', () => {
          if (formMode !== 'closed') return;
          setSelectedStationId(station.id);
          setDrawerOpen(true); setActivePanel('context');
          setStationError(null);
          focusLocation([station.latitude, station.longitude], 16);
        });
      }

      marker.addTo(layer);
      stationMarkers.set(station.id, marker);
    });
    })();
    cleanup(() => { stationLayer?.eachLayer(item => item.off()); stationLayer?.clearLayers(); stationMarkers.clear(); });
  }, { immediate: true });

  watch([mapInstanceRef, () => s().formMode, () => s().stationForm, () => o().workspace], (_value, _previous, cleanup) => {
    const { workspace } = o(); const { formMode, stationForm } = s();
    (() => {
    const layer = draftLayer;
    if (!layer) return;
    layer.clearLayers();
    if (workspace !== 'stations' || formMode === 'closed') return;

    const latitude = validCoordinate(stationForm.latitude, -90, 90);
    const longitude = validCoordinate(stationForm.longitude, -180, 180);
    if (latitude === null || longitude === null) return;
    const position: L.LatLngExpression = [latitude, longitude];
    const radius = Number(stationForm.checkinRadiusMeters);

    if (Number.isFinite(radius) && radius >= 10 && radius <= 1000) {
      L.circle(position, {
        radius,
        color: '#f59e0b',
        dashArray: '6 6',
        weight: 2,
        opacity: 0.9,
        fillColor: '#f59e0b',
        fillOpacity: 0.14,
        interactive: false,
      }).addTo(layer);
    }

    const marker = L.marker(position, {
      icon: createStationIcon('draft'),
      draggable: true,
      zIndexOffset: 1000,
      bubblingMouseEvents: false,
    });

    marker.on('dragend', () => {
      const coordinates = marker.getLatLng();
      setStationForm((current) => ({
        ...current,
        latitude: coordinates.lat.toFixed(6),
        longitude: coordinates.lng.toFixed(6),
      }));
      setPickingLocation(false);
    });

    marker.bindTooltip('Kéo để tinh chỉnh vị trí trạm', { permanent: true, direction: 'top', offset: [0, -38] });
    marker.addTo(layer);
    })();
    cleanup(() => { draftLayer?.eachLayer(item => item.off()); draftLayer?.clearLayers(); });
  }, { immediate: true });

  watch([mapInstanceRef, () => o().theme, () => o().showTraffic, basemapRetry], (_value, _previous, cleanup) => {
    const { theme, showTraffic } = o(); const mapReady = !!mapInstanceRef.value;
    const dispose = (() => {
    const map = mapInstanceRef.value;
    if (!map || !mapReady) return;
    basemapStatus.value = 'loading';

    if (tileLayer) {
      map.removeLayer(tileLayer);
      tileLayer = null;
    }
    const baseType = theme === 'google-satellite' ? 'y' : 'm';
    const layerType = showTraffic ? `${baseType},traffic` : baseType;
    const newTileLayer = L.tileLayer(
      `https://{s}.google.com/vt/lyrs=${layerType}&hl=vi&gl=VN&x={x}&y={y}&z={z}`,
      {
        maxZoom: 20,
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
        className: theme === 'google-dark' ? 'dark-map-tiles' : '',
        attribution: '&copy; Google Maps',
        updateWhenZooming: false,
        updateWhenIdle: true,
        keepBuffer: 4,
      }
    );

    const retryTimers = new Set<ReturnType<typeof setTimeout>>();
    let loadedTile = false, failedTile = false;
    let timeout: ReturnType<typeof setTimeout> | null = null;
    const clearTimeoutCheck = () => {
      if (timeout !== null) clearTimeout(timeout);
      timeout = null;
    };
    newTileLayer.on('loading', () => {
      loadedTile = false;
      failedTile = false;
      basemapStatus.value = 'loading';
      clearTimeoutCheck();
      timeout = setTimeout(() => {
        if (!loadedTile) basemapStatus.value = 'error';
      }, 10_000);
    });
    // Auto-retry transient failed tiles (e.g. rate-limit or network timeout)
    newTileLayer.on('tileerror', (event) => {
      failedTile = true;
      const tile = (event as L.TileEvent).tile as HTMLImageElement;
      if (!tile) return;
      const retryCount = Number(tile.dataset.retryCount || '0');
      if (retryCount < 2) {
        tile.dataset.retryCount = String(retryCount + 1);
        const originalSrc = tile.src;
        const timer = setTimeout(() => {
          retryTimers.delete(timer); tile.src = originalSrc;
        }, 600 * (retryCount + 1));
        retryTimers.add(timer);
      }
    });
    newTileLayer.on('tileload', (event) => {
      loadedTile = true;
      basemapStatus.value = 'ready';
      clearTimeoutCheck();
      const tile = (event as L.TileEvent).tile as HTMLImageElement;
      if (tile) delete tile.dataset.retryCount;
    });

    newTileLayer.on('load', () => {
      if (failedTile && !loadedTile) basemapStatus.value = 'error';
    });

    newTileLayer.addTo(map).bringToBack();
    tileLayer = newTileLayer;

    return () => {
      retryTimers.forEach(timer => clearTimeout(timer));
      clearTimeoutCheck();
      if (map.hasLayer(newTileLayer)) map.removeLayer(newTileLayer);
      newTileLayer.off();
      if (tileLayer === newTileLayer) {
        tileLayer = null;
      }
    };
    })();
    cleanup(() => { dispose?.();  });
  }, { immediate: true });

  watch([mapInstanceRef, () => o().plannedRoute, () => o().showRoutes, () => o().workspace, () => o().hasSimulationRoute], (_value, _previous, cleanup) => {
    const { plannedRoute, showRoutes, workspace, hasSimulationRoute } = o();
    const dispose = (() => {
    const layer = plannedRouteLayer;
    const map = mapInstanceRef.value;
    if (!layer || !map) return;

    layer.clearLayers();
    plannedRouteBounds.value = null;

    if (!showRoutes || !plannedRoute) {
      return;
    }

    let toastTimer: number | null = null;
    let hasDecodeError = false;
    const allCoords: [number, number][] = [];
    const decodedSections: { coords: [number, number][]; section: RouteDetail['sections'][number] }[] = [];

    // M2-04: Decode polyline for each section. If ANY section fails or returns empty coordinates, fail the whole route!
    for (const section of plannedRoute.sections) {
      try {
        const coords = decodeFlexiblePolyline(section.encodedPolyline);
        if (coords.length === 0) {
          hasDecodeError = true;
          break;
        }
        decodedSections.push({ coords, section });
        allCoords.push(...coords);
      } catch {
        hasDecodeError = true;
        break;
      }
    }

    // If any section is broken, reject partial geometry: clear layer, don't draw markers, notify user
    if (hasDecodeError) {
      layer.clearLayers();
      toastTimer = window.setTimeout(() => {
        setToast('Lỗi: Hình học đường đi (polyline) của tuyến bị hỏng, không thể hiển thị lộ trình.');
      }, 0);
      return () => {
        if (toastTimer !== null) window.clearTimeout(toastTimer);
      };
    }

    if (allCoords.length > 0 && !hasSimulationRoute) {
      /*
       * Keep each HERE section as its own Leaflet path.  Concatenating
       * sections into one array creates a straight connector whenever HERE
       * rounds a via endpoint differently in adjacent sections; that
       * connector can visibly cut across buildings instead of following a
       * road.
       */
      for (const { coords: sectionCoords } of decodedSections) {
        // 1. Google Maps subtle route shadow (đổ bóng mỏng nhẹ giúp tách biệt trên mọi nền bản đồ)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: 'rgba(24, 90, 188, 0.24)',
          weight: 10,
          opacity: 1,
          lineCap: 'round',
          lineJoin: 'round',
          interactive: false,
        }).addTo(layer);

        // 2. Google Maps outer casing (viền xanh đậm Google Blue 700 định hình đường đi sắc nét)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: '#1967d2',
          weight: 7.5,
          opacity: 0.96,
          lineCap: 'round',
          lineJoin: 'round',
          interactive: false,
        }).addTo(layer);

        // 3. Google Maps inner core (lõi xanh Google Primary Blue rực rỡ đặc trưng của Google Maps)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: '#4285f4',
          weight: 5,
          opacity: 1.0,
          lineCap: 'round',
          lineJoin: 'round',
          interactive: false,
        }).addTo(layer);

      }
    }

    // Draw numbered stop markers
    plannedRoute.stops.forEach((stop) => {
      const marker = L.marker([stop.latitude, stop.longitude], {
        icon: createRouteStopIcon(stop.sequenceNumber, stop.role),
        zIndexOffset: 700 + stop.sequenceNumber,
      });

      const roleName = stop.role === 'START' ? 'Khởi hành' : stop.role === 'END' ? 'Về đích' : 'Đón/trả';

      // H-02: Use DOM node and textContent to prevent XSS in Leaflet tooltip
      const tooltipContainer = document.createElement('div');
      const strongEl = document.createElement('strong');
      strongEl.textContent = `#${stop.sequenceNumber} - ${stop.stationName}`;
      tooltipContainer.appendChild(strongEl);
      tooltipContainer.appendChild(document.createTextNode(` (${roleName})`));
      tooltipContainer.appendChild(document.createElement('br'));
      tooltipContainer.appendChild(document.createTextNode(`Dừng: ${stop.dwellDurationSeconds}s`));

      marker.bindTooltip(tooltipContainer, { direction: 'top', offset: [0, -14], opacity: 0.95 });

      marker.addTo(layer);
    });

    // Fit bounds only when all sections decoded successfully
    if (allCoords.length > 0) {
      plannedRouteBounds.value = L.latLngBounds(allCoords);
    }
    if (allCoords.length > 0 && workspace !== 'simulation' && mapContainerRef.value?.clientWidth) {
      map.invalidateSize({ pan: false });
      fitBounds(L.latLngBounds(allCoords));
    }

    return () => {
      if (toastTimer !== null) window.clearTimeout(toastTimer);
    };
    })();
    cleanup(() => { dispose?.(); plannedRouteLayer?.eachLayer(item => item.off()); plannedRouteLayer?.clearLayers(); });
  }, { immediate: true });

  watch([mapInstanceRef, () => o().draftStops, () => s().stations, () => o().workspace, () => o().plannedRoute, () => o().selectedDraftStopId, () => o().showRoutes], (_value, _previous, cleanup) => {
    const { draftStops, workspace, plannedRoute, selectedDraftStopId, showRoutes } = o(); const { stations } = s();
    (() => {
    const layer = routeDraftLayer;
    if (!layer) return;
    layer.clearLayers();
    if (workspace !== 'routes' || !showRoutes || plannedRoute) return;

    // Google Maps style draft connector line between draft stations
    const draftCoords: [number, number][] = [];
    draftStops.forEach((stop) => {
      const station = stations.find(item => item.id === stop.stationId && item.active);
      if (station) {
        draftCoords.push([station.latitude, station.longitude]);
      }
    });

    if (draftCoords.length >= 2) {
      // Draft Casing - Google Maps Style
      L.polyline(draftCoords, {
        pane: 'routePane',
        color: '#1967d2',
        weight: 6,
        opacity: 0.92,
        dashArray: '6, 8',
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false,
      }).addTo(layer);

      // Draft Inner Google Blue
      L.polyline(draftCoords, {
        pane: 'routePane',
        color: '#4285f4',
        weight: 4,
        opacity: 1.0,
        dashArray: '6, 8',
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false,
      }).addTo(layer);
    }

    draftStops.forEach((stop, index) => {
      const station = stations.find(item => item.id === stop.stationId && item.active);
      if (!station) return;
      const role = index === 0 ? 'START' : index === draftStops.length - 1 ? 'END' : 'STOP';
      const marker = L.marker([station.latitude, station.longitude], {
        icon: createRouteStopIcon(index + 1, role),
        zIndexOffset: stop.id === selectedDraftStopId ? 1100 : 900,
        keyboard: true,
        title: `Điểm nháp ${index + 1}: ${station.name}`,
      });
      const label = document.createElement('span');
      label.textContent = `Nháp #${index + 1} · ${station.name} · Chưa tính tuyến`;
      marker.bindTooltip(label, { direction: 'top' });
      marker.on('click', () => {
        setSelectedDraftStopId(stop.id);
        setDrawerOpen(true);
        setActivePanel('context');
        focusLocation([station.latitude, station.longitude]);
      });
      marker.addTo(layer);
    });
    })();
    cleanup(() => { routeDraftLayer?.eachLayer(item => item.off()); routeDraftLayer?.clearLayers(); });
  }, { immediate: true });
  return { plannedRouteBounds, basemapStatus, retryBasemap };
}
