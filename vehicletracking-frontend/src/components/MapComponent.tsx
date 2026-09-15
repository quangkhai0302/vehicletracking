import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type FC } from 'react';
import L from 'leaflet';
import { decodeFlexiblePolyline } from '../services/polyline';
import type { MapTheme } from '../types/map';
import type { TripDetail } from '../types/fleet';
import type { RouteDetail, RouteDraftStop, RouteStopRole } from '../types/route';
import { useLiveOperations } from '../hooks/useLiveOperations';
import { useSimulator } from '../hooks/useSimulator';
import { useSimulationFleet } from '../hooks/useSimulationFleet';
import { usePlannedVehicleAnchors } from '../hooks/usePlannedVehicleAnchors';
import { useVehicleMarkers, type VehicleMarkerAnchor } from '../hooks/useVehicleMarkers';
import { useSelectedVehicleRoute } from '../hooks/useSelectedVehicleRoute';
import type { WorkspaceMode } from '../types/workspace';
import { MapControls } from './MapControls';
import { StationDrawer } from './StationDrawer';
import { StationPanel } from './StationPanel';
import { FleetWorkspace } from './fleet/FleetWorkspace';
import { RouteWorkspace } from './route/RouteWorkspace';
import './route/route.css';
import { Bell, ChevronDown, ChevronUp, Crosshair, List, PanelLeftClose, Play, X } from 'lucide-react';
import { useMapCamera } from '../hooks/useMapCamera';
import { useStationWorkspace } from '../hooks/useStationWorkspace';
import { useCompactLayout } from '../hooks/useCompactLayout';
import { ModeBar } from './operations/ModeBar';
import { SimulatorPanel } from './operations/SimulatorPanel';
import { TripTrafficSummary } from './operations/TripTrafficSummary';
import { AlertStream } from './operations/AlertStream';
import { ConfirmStationDelete } from './operations/ConfirmStationDelete';
import { useTraffic } from '../hooks/useTraffic';
import { TrafficLayer } from './traffic/TrafficLayer';

const RouteInspectionLayer = lazy(() => import('./route/RouteInspectionLayer').then(module => ({ default: module.RouteInspectionLayer })));
const SimulationFleetLayer = lazy(() => import('./operations/SimulationFleetLayer').then(module => ({ default: module.SimulationFleetLayer })));
const SimulationRoutesLayer = lazy(() => import('./operations/SimulationRoutesLayer').then(module => ({ default: module.SimulationRoutesLayer })));

const HCMC_CENTER: [number, number] = [10.7769, 106.7009];

function createRouteStopIcon(sequenceNumber: number, role: RouteStopRole): L.DivIcon {
  const roleClass = role.toLowerCase();
  return L.divIcon({
    className: 'route-stop-div-icon',
    html: `<div class="route-stop-map-marker ${roleClass}">${sequenceNumber}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    popupAnchor: [0, -16],
    tooltipAnchor: [0, -14],
  });
}

function createStationIcon(state: 'default' | 'selected' | 'muted' | 'draft'): L.DivIcon {
  return L.divIcon({
    className: 'station-div-icon',
    html: `<span class="station-map-marker ${state}"><span class="station-map-marker-core"></span></span>`,
    iconSize: [34, 42],
    iconAnchor: [17, 38],
    popupAnchor: [0, -36],
    tooltipAnchor: [0, -32],
  });
}

function validCoordinate(value: string, min: number, max: number): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= min && parsed <= max ? parsed : null;
}

export const MapComponent: FC = () => {
  const rootRef = useRef<HTMLElement>(null);
  const [workspace, setWorkspace] = useState<WorkspaceMode>('tracking');
  const compact = useCompactLayout();
  const [activePanel, setActivePanel] = useState<'context' | 'simulator' | 'alerts' | null>('context');
  const [drawerOpen, setDrawerOpen] = useState(true);
  const [sheetExpanded, setSheetExpanded] = useState(false);
  const [simulatorExpanded, setSimulatorExpanded] = useState(true);
  const [alertsExpanded, setAlertsExpanded] = useState(true);
  const [showStations, setShowStations] = useState(true);
  const [showRoutes, setShowRoutes] = useState(true);
  const [showTraffic, setShowTraffic] = useState(true);
  const [draftStops, setDraftStops] = useState<RouteDraftStop[]>([]);
  const [selectedDraftStopId, setSelectedDraftStopId] = useState<string | null>(null);
  const routeDraftLayerRef = useRef<L.LayerGroup | null>(null);
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const [mapReady, setMapReady] = useState(false);
  const tileLayerRef = useRef<L.TileLayer | null>(null);
  const stationLayerRef = useRef<L.LayerGroup | null>(null);
  const draftLayerRef = useRef<L.LayerGroup | null>(null);
  const routeLayerRef = useRef<L.LayerGroup | null>(null);
  const plannedRouteLayerRef = useRef<L.LayerGroup | null>(null);
  const plannedRouteBoundsRef = useRef<L.LatLngBounds | null>(null);
  const stationMarkerRef = useRef<Map<number, L.Marker>>(new Map());

  const [theme, setTheme] = useState<MapTheme>('google-roadmap');
  const [toast, setToast] = useState<string | null>(null);
  const live = useLiveOperations();
  const simulator = useSimulator(live.snapshot, setToast);
  const simulationFleet = useSimulationFleet(live.snapshot, workspace === 'simulation');
  const persistedVehicleAnchors = usePlannedVehicleAnchors(live.snapshot);
  const traffic = useTraffic(mapInstanceRef, showTraffic, mapReady);
  const trafficStatus = traffic.incidents?.status;
  const trafficMessage = traffic.loading
    ? 'Đang tải giao thông…'
    : traffic.error ? 'Chưa tải được giao thông. Thử lại trong lớp bản đồ.' : (trafficStatus === 'STALE' ? 'Đang dùng dữ liệu gần nhất.'
      : trafficStatus === 'UNAVAILABLE' ? 'Giao thông chưa khả dụng.'
        : showTraffic ? 'Đang hiển thị'
          : 'Tạm tắt');
  const connectionLabel = live.connection === 'live' ? 'Đang cập nhật trực tiếp' : live.connection === 'connecting' ? 'Đang kết nối…' : 'Mất kết nối · Đang thử lại';
  const { focusLocation, fitBounds, getVisibleCenter, releaseFocus } = useMapCamera(rootRef, mapInstanceRef);
  const stationWorkspace = useStationWorkspace({
    focusLocation, onToast: setToast,
    onPickStart: () => setActivePanel(null),
    onPickEnd: () => { setDrawerOpen(true); setActivePanel('context'); },
  });
  const {
    stations, loadingStations, savingStation, deletingStation, stationError, selectedStationId,
    formMode, editingStation, stationForm, pickingLocation, deleteCandidate, selectedStation,
    setStationForm, setPickingLocation, setSelectedStationId, setStationError, setDeleteCandidate,
    handleBeginCreate, handleSelectStation, handleBeginEdit, handleCloseStationDrawer,
    handleSaveStation, handleFieldChange, handleDeactivate,
  } = stationWorkspace;
  const contextVisible = drawerOpen && !(pickingLocation && workspace === 'stations') && (!compact || activePanel === 'context');
  const selectMode = (mode: WorkspaceMode) => {
    if (mode !== 'stations') setPickingLocation(false);
    setWorkspace(mode);
    setSheetExpanded(false);
    setDrawerOpen(true);
    setActivePanel(mode === 'simulation' ? 'simulator' : 'context');
    if (mode === 'simulation') setSimulatorExpanded(true);
  };
  const openPanel = (panel: 'context' | 'simulator' | 'alerts') => {
    setPickingLocation(false);
    setActivePanel(panel);
    setSheetExpanded(false);
    if (panel === 'context') { setDrawerOpen(true); setPickingLocation(false); }
    if (panel === 'simulator') setSimulatorExpanded(true);
    if (panel === 'alerts') setAlertsExpanded(true);
  };

  // Route Planning Display State (Data flow managed by RouteWorkspace)
  const [editorRoute, setPlannedRoute] = useState<RouteDetail | null>(null);
  const [createdVehicleAnchors, setCreatedVehicleAnchors] = useState<VehicleMarkerAnchor[]>([]);
  const handleTripCreated = useCallback((detail: TripDetail) => {
    const start = [...detail.stops].sort((a, b) => a.sequenceNumber - b.sequenceNumber)[0];
    if (!start || !Number.isFinite(start.latitude) || !Number.isFinite(start.longitude)) {
      setToast('Chuyến đã tạo nhưng chưa có tọa độ trạm đầu để hiển thị xe.');
      return;
    }
    setCreatedVehicleAnchors(current => [
      ...current.filter(anchor => anchor.vehicleId !== detail.trip.vehicleId && anchor.tripId !== detail.trip.id),
      {
        vehicleId: detail.trip.vehicleId,
        tripId: detail.trip.id,
        vehiclePlateNumber: detail.trip.vehiclePlateNumber,
        vehicleType: detail.trip.vehicleType,
        latitude: start.latitude,
        longitude: start.longitude,
      },
    ]);
    focusLocation([start.latitude, start.longitude], 16);
  }, [focusLocation]);

  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);
  const [tripSelection, setTripSelection] = useState<{ tripId: number | null } | null>(null);
  const [followingVehicle, setFollowingVehicle] = useState(false);
  const plannedVehicleAnchors = useMemo(() => {
    const byVehicle = new Map<number, VehicleMarkerAnchor>();
    persistedVehicleAnchors.forEach(anchor => byVehicle.set(anchor.vehicleId, anchor));
    createdVehicleAnchors.forEach(anchor => {
      const trip = live.snapshot?.trips.find(item => item.id === anchor.tripId);
      if (!trip || (trip.status !== 'COMPLETED' && trip.status !== 'CANCELLED')) byVehicle.set(anchor.vehicleId, anchor);
    });
    return [...byVehicle.values()];
  }, [persistedVehicleAnchors, createdVehicleAnchors, live.snapshot]);
  const mapSnapshot = useMemo(() => {
    if (!live.snapshot || workspace !== 'simulation') return live.snapshot;
    const waitingIds = new Set(simulationFleet.previews.map(item=>item.trip.vehicleId));
    const fleetTripIds = new Set(simulationFleet.trips.map(trip=>trip.id));
    return {...live.snapshot, positions:live.snapshot.positions.filter(point=>!waitingIds.has(point.vehicleId)
      && (point.source!=='SIMULATOR' || fleetTripIds.has(point.tripId)))};
  }, [live.snapshot,workspace,simulationFleet.previews,simulationFleet.trips]);
  const selectedVehicle = useMemo(
    () => mapSnapshot?.positions.find(point => point.vehicleId === selectedVehicleId) ?? null,
    [selectedVehicleId, mapSnapshot]
  );
  const markerAnchors = useMemo(
    () => workspace === 'simulation'
      ? plannedVehicleAnchors.filter(anchor => !simulationFleet.previews.some(item => item.trip.id === anchor.tripId))
      : plannedVehicleAnchors,
    [workspace, plannedVehicleAnchors, simulationFleet.previews]
  );
  const selectedPlannedVehicle = plannedVehicleAnchors.find(anchor => anchor.vehicleId === selectedVehicleId) ?? null;
  const selectedWaitingVehicle = simulationFleet.previews.find(item => item.trip.vehicleId === selectedVehicleId);
  const selectedTripId = selectedVehicle?.tripId
    ?? (selectedVehicleId === null ? null : selectedPlannedVehicle?.tripId ?? selectedWaitingVehicle?.trip.id
      ?? (simulator.trip?.vehicleId === selectedVehicleId ? simulator.trip.id : null));
  const vehicleRoute = useSelectedVehicleRoute(selectedTripId, setToast);
  const plannedRoute = workspace === 'routes' ? editorRoute : workspace === 'stations' ? null : vehicleRoute;
  const selectedSimulationRoutes = simulationFleet.routes.filter(item => item.trip.id === selectedTripId);
  const hasSimulationRoute = workspace === 'simulation' && selectedSimulationRoutes.length > 0;
  const selectVehicleTrip = (vehicleId: number, tripId: number) => {
    if (simulator.busy) return;
    setSelectedVehicleId(vehicleId);
    setTripSelection({ tripId });
    simulator.select(tripId);
    setFollowingVehicle(false);
    if (workspace === 'routes' || workspace === 'stations') {
      setWorkspace('tracking'); setPickingLocation(false);
    }
    setDrawerOpen(true); setSimulatorExpanded(true);
    setActivePanel('context');
  };
  const clearVehicleSelection = () => {
    if (simulator.busy) return;
    setSelectedVehicleId(null); setFollowingVehicle(false);
    setTripSelection({ tripId: null }); simulator.select(null);
  };
  useVehicleMarkers({ mapRef: mapInstanceRef, snapshot: mapSnapshot, now: live.now,
    plannedPositions: markerAnchors, visible: true, selectedId: selectedVehicleId, groupSelection: workspace==='simulation',
    following: followingVehicle, onSelect: id => {
      const point = mapSnapshot?.positions.find(item=>item.vehicleId===id) ?? plannedVehicleAnchors.find(item=>item.vehicleId===id);
      if (point) selectVehicleTrip(id, point.tripId);
    }, onFocus: focusLocation });

  const focusVehicle = (id: number) => {
    const point = mapSnapshot?.positions.find(item => item.vehicleId === id) ?? plannedVehicleAnchors.find(item => item.vehicleId === id);
    if (point && !simulator.busy) {
      selectVehicleTrip(id, point.tripId); focusLocation([point.latitude,point.longitude],16);
    }
  };
  const openSimulation = (id: number) => {
    const trip = live.snapshot?.trips.find(item => item.id === id);
    if (simulator.busy) return;
    if (trip) selectVehicleTrip(trip.vehicleId, id);
    else { setSelectedVehicleId(null); setTripSelection({ tripId: id }); simulator.select(id); }
    selectMode('simulation');
  };
  const selectSimulationVehicle = (tripId: number) => {
    if (simulator.busy) return;
    const preview = simulationFleet.previews.find(item=>item.trip.id===tripId);
    const trip = preview?.trip ?? live.snapshot?.trips.find(item=>item.id===tripId);
    if (!trip) return;
    selectVehicleTrip(trip.vehicleId, tripId);
    setWorkspace('simulation'); setActivePanel('simulator');
    const point = live.snapshot?.positions.find(item => item.tripId === tripId);
    if (preview) focusLocation([preview.start.latitude,preview.start.longitude],16);
    else if (point) focusLocation([point.latitude,point.longitude],16);
  };
  const fleetPoints: [number,number][] = [
    ...(showRoutes ? selectedSimulationRoutes.flatMap(item=>item.segments.flat()) : []),
    ...simulationFleet.previews.map(item=>[item.start.latitude,item.start.longitude] as [number,number]),
    ...(mapSnapshot?.positions.filter(point=>simulationFleet.trips.some(trip=>trip.id===point.tripId))
      .map(point=>[point.latitude,point.longitude] as [number,number]) ?? []),
  ];
  const fleetPointsKey = JSON.stringify(fleetPoints);
  const fleetFitted = useRef(false);
  useEffect(() => {
    if (workspace !== 'simulation') { fleetFitted.current=false; return; }
    if (!mapReady || simulationFleet.loading || fleetFitted.current || fleetPointsKey==='[]') return;
    fleetFitted.current=true;
    fitBounds(L.latLngBounds(JSON.parse(fleetPointsKey) as [number,number][]));
  }, [workspace,mapReady,simulationFleet.loading,fleetPointsKey,fitBounds]);
  const fitSimulationFleet = () => { if (fleetPoints.length) { setFollowingVehicle(false); fitBounds(L.latLngBounds(fleetPoints)); } };

  // Auto hide toast
  useEffect(() => {
    if (!toast) return;
    const timeoutId = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
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

    routeDraftLayerRef.current = L.layerGroup().addTo(map);

    routeLayerRef.current = L.layerGroup().addTo(map);
    plannedRouteLayerRef.current = L.layerGroup().addTo(map);
    stationLayerRef.current = L.layerGroup().addTo(map);
    draftLayerRef.current = L.layerGroup().addTo(map);

    // User drag starts -> Stop following vehicle
    const onDragStart = () => {
      setFollowingVehicle(false);
      releaseFocus();
    };

    map.on('dragstart', onDragStart);
    mapInstanceRef.current = map;
    setMapReady(true);

    return () => {
      map.off('dragstart', onDragStart);
      map.remove();
      mapInstanceRef.current = null;
      setMapReady(false);
      stationLayerRef.current = null;
      draftLayerRef.current = null;
      routeLayerRef.current = null;
      plannedRouteLayerRef.current = null;
      routeDraftLayerRef.current = null;
    };
  }, [releaseFocus]);

  // Map click for picking location
  useEffect(() => {
    const map = mapInstanceRef.current;
    const mapContainer = mapContainerRef.current;
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
  }, [formMode, pickingLocation, workspace, setStationForm, setPickingLocation, focusLocation]);

  // Render Stations Layer
  useEffect(() => {
    const layer = stationLayerRef.current;
    const map = mapInstanceRef.current;
    if (!layer || !map) return;
    layer.clearLayers();
    stationMarkerRef.current.clear();

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
      marker.bindTooltip(stationTooltip, { direction: 'top', offset: [0, -28], opacity: 0.9 });

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
      stationMarkerRef.current.set(station.id, marker);
    });
  }, [editingStation?.id, formMode, selectedStationId, stations, workspace, showStations, focusLocation, setSelectedStationId, setStationError, showRoutes, plannedRoute, draftStops]);

  // Render Draft Station (Create/Edit)
  useEffect(() => {
    const layer = draftLayerRef.current;
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

    marker.bindTooltip('Kéo để tinh chỉnh vị trí trạm', { permanent: true, direction: 'top', offset: [0, -28] });
    marker.addTo(layer);
  }, [formMode, stationForm, workspace, setStationForm, setPickingLocation]);

  // Base map tile layer. Traffic is rendered separately by TrafficLayer
  // through the backend HERE Raster Tile proxy, so the Google base layer
  // must never add its own `traffic` overlay.
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !mapReady) return;

    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
      tileLayerRef.current = null;
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

    // Auto-retry transient failed tiles (e.g. rate-limit or network timeout)
    newTileLayer.on('tileerror', (event) => {
      const tile = (event as L.TileEvent).tile as HTMLImageElement;
      if (!tile) return;
      const retryCount = Number(tile.dataset.retryCount || '0');
      if (retryCount < 2) {
        tile.dataset.retryCount = String(retryCount + 1);
        const originalSrc = tile.src;
        setTimeout(() => {
          tile.src = originalSrc;
        }, 600 * (retryCount + 1));
      }
    });

    newTileLayer.addTo(map).bringToBack();
    tileLayerRef.current = newTileLayer;

    return () => {
      if (tileLayerRef.current && map) {
        map.removeLayer(tileLayerRef.current);
        tileLayerRef.current = null;
      }
    };
  }, [theme, showTraffic, mapReady]);

  // Render Planned Route (Polyline & Stop markers)
  useEffect(() => {
    const layer = plannedRouteLayerRef.current;
    const map = mapInstanceRef.current;
    if (!layer || !map) return;

    layer.clearLayers();
    plannedRouteBoundsRef.current = null;

    if (!showRoutes || !plannedRoute) {
      return;
    }

    let toastTimer: number | null = null;
    let hasDecodeError = false;
    const allCoords: [number, number][] = [];
    const decodedSections: [number, number][][] = [];

    // M2-04: Decode polyline for each section. If ANY section fails or returns empty coordinates, fail the whole route!
    for (const section of plannedRoute.sections) {
      try {
        const coords = decodeFlexiblePolyline(section.encodedPolyline);
        if (coords.length === 0) {
          hasDecodeError = true;
          break;
        }
        decodedSections.push(coords);
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
      for (const sectionCoords of decodedSections) {
        // 1. Google Maps ambient glow (giúp nổi bật trên nền bản đồ tối hoặc vệ tinh)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: '#0b57d0',
          weight: 12,
          opacity: 0.42,
          lineCap: 'round',
          lineJoin: 'round',
          interactive: false,
        }).addTo(layer);

        // 2. Google Maps outer casing (viền xanh đậm định hình đường đi chuẩn Google Maps)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: '#0842a0',
          weight: 8,
          opacity: 1,
          lineCap: 'round',
          lineJoin: 'round',
          interactive: false,
        }).addTo(layer);

        // 3. Google Maps inner core (lớp lõi xanh dương đặc trưng Google Maps #4285f4)
        L.polyline(sectionCoords, {
          pane: 'routePane',
          color: '#1a73e8',
          weight: 5.5,
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
      plannedRouteBoundsRef.current = L.latLngBounds(allCoords);
    }
    if (allCoords.length > 0 && workspace !== 'simulation' && mapContainerRef.current?.clientWidth) {
      map.invalidateSize({ pan: false });
      fitBounds(L.latLngBounds(allCoords));
    }

    return () => {
      if (toastTimer !== null) window.clearTimeout(toastTimer);
    };
  }, [plannedRoute, showRoutes, fitBounds, workspace, hasSimulationRoute]);

  // Draft markers communicate order only; the POST response supplies road geometry.
  useEffect(() => {
    const layer = routeDraftLayerRef.current;
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
      // Draft Casing
      L.polyline(draftCoords, {
        pane: 'routePane',
        color: '#0842a0',
        weight: 6,
        opacity: 0.95,
        dashArray: '6, 8',
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false,
      }).addTo(layer);

      // Draft Inner Google Blue
      L.polyline(draftCoords, {
        pane: 'routePane',
        color: '#1a73e8',
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
  }, [draftStops, stations, workspace, plannedRoute, selectedDraftStopId, showRoutes, focusLocation]);

  const focusDraftStop = (id: string) => {
    setSelectedDraftStopId(id);
    const stop = draftStops.find(item => item.id === id);
    const station = stations.find(item => item.id === stop?.stationId);
    if (station) focusLocation([station.latitude, station.longitude], 16);
  };
  const handleFit = () => {
    if (workspace === 'simulation' && fleetPoints.length) { fitSimulationFleet(); return; }
    if (plannedRouteBoundsRef.current && showRoutes) {
      fitBounds(plannedRouteBoundsRef.current);
      return;
    }
    const draftStations = draftStops.map(stop => stations.find(station => station.id === stop.stationId)).filter(station => station !== undefined);
    const points = workspace === 'routes' && draftStations.length ? draftStations : showStations ? stations : [];
    if (points.length) fitBounds(L.latLngBounds(points.map(station => [station.latitude, station.longitude])));
  };

  // Helper: Pick map center coordinates
  const handlePickMapCenter = () => {
    const map = mapInstanceRef.current;
    if (!map) return;
    const center = getVisibleCenter();
    if (!center) return;
    setStationForm((cur) => ({
      ...cur,
      latitude: center.lat.toFixed(6),
      longitude: center.lng.toFixed(6),
    }));
    setPickingLocation(false);
    setDrawerOpen(true); setActivePanel('context');
    setToast('Đã gán tọa độ tâm bản đồ vào biểu mẫu.');
    focusLocation(center);
  };

  return (
    <main ref={rootRef} className="map-first" data-workspace={workspace} data-sheet-expanded={sheetExpanded} data-drawer-open={contextVisible}>
      <div id="main-map" ref={mapContainerRef} className="map-canvas" aria-label="Bản đồ tương tác" tabIndex={-1} />
      <TrafficLayer mapRef={mapInstanceRef} mapReady={mapReady} visible={showTraffic} incidents={traffic.incidents} />
      {workspace==='simulation' && <Suspense fallback={null}><SimulationFleetLayer mapRef={mapInstanceRef} mapReady={mapReady} visible
        vehicles={simulationFleet.previews} onSelect={selectSimulationVehicle} />
        <SimulationRoutesLayer mapRef={mapInstanceRef} mapReady={mapReady} visible={showRoutes}
          routes={selectedSimulationRoutes} selectedTripId={selectedTripId} onSelect={selectSimulationVehicle} /></Suspense>}
      {plannedRoute && <Suspense fallback={null}>
        <RouteInspectionLayer mapRef={mapInstanceRef} mapReady={mapReady} route={plannedRoute}
          visible={showRoutes && !pickingLocation && workspace !== 'simulation'} trafficEnabled={showTraffic} />
      </Suspense>}
      <ModeBar mode={workspace} onChange={selectMode} connectionLabel={connectionLabel} />
      <div className="live-follow glass-panel" data-map-edge="top" hidden={!selectedVehicle && !selectedWaitingVehicle && !selectedPlannedVehicle}>
        {!selectedVehicle && selectedWaitingVehicle && <div className="live-follow-actions">
          <span>{selectedWaitingVehicle.trip.vehiclePlateNumber} · Chờ mô phỏng</span>
          <button aria-label="Bỏ chọn xe" disabled={simulator.busy} onClick={clearVehicleSelection}>×</button>
        </div>}
        {!selectedVehicle && !selectedWaitingVehicle && selectedPlannedVehicle && <div className="live-follow-actions">
          <span>{selectedPlannedVehicle.vehiclePlateNumber} · Chưa khởi hành</span>
          <button aria-label="Bỏ chọn xe" disabled={simulator.busy} onClick={clearVehicleSelection}>×</button>
        </div>}
        {selectedVehicle && <>
        <div className="live-follow-actions">
          <span>{live.snapshot?.trips.find(trip => trip.id === selectedVehicle.tripId)?.vehiclePlateNumber ?? selectedVehicle.vehicleId} · {selectedVehicle.source === 'SIMULATOR' ? 'Giả lập' : 'GPS'}</span>
          <button aria-pressed={followingVehicle} onClick={() => { setFollowingVehicle(value => !value); if (!followingVehicle) focusLocation([selectedVehicle.latitude, selectedVehicle.longitude], 16); }}>{followingVehicle ? 'Bỏ theo xe' : 'Theo xe'}</button>
          <button aria-label="Bỏ chọn xe" disabled={simulator.busy} onClick={clearVehicleSelection}>×</button>
        </div>
        <TripTrafficSummary key={`${selectedVehicle.tripId}:${selectedVehicle.attemptNumber ?? 1}`} context="vehicle" tripId={selectedVehicle.tripId} position={selectedVehicle}
          run={selectedVehicle.source === 'SIMULATOR' ? live.snapshot?.simulations.find(run => run.tripId === selectedVehicle.tripId) ?? null : null}
          now={live.now} connection={live.connection} />
        </>}
      </div>

      <aside className="context-drawer glass-panel" data-map-edge="left" hidden={!contextVisible} aria-label="Bảng dữ liệu vận hành">
        <div className="floating-panel-heading">
          <span><List size={15} />{workspace === 'tracking' || workspace === 'simulation' ? 'ĐỘI XE' : 'THIẾT LẬP LỘ TRÌNH'}</span>
          <div>
            <button className="sheet-expand" onClick={() => setSheetExpanded(value => !value)} aria-label={sheetExpanded ? 'Thu chiều cao bảng' : 'Mở rộng bảng'}>{sheetExpanded ? <ChevronDown size={16} /> : <ChevronUp size={16} />}</button>
            <button onClick={() => { setDrawerOpen(false); setActivePanel(null); rootRef.current?.querySelector<HTMLButtonElement>('.panel-launchers button')?.focus(); }} aria-label="Thu bảng dữ liệu"><PanelLeftClose size={16} /></button>
          </div>
        </div>
        <div className="planning-tabs" hidden={workspace !== 'stations' && workspace !== 'routes'} aria-label="Dữ liệu lộ trình">
          <button aria-pressed={workspace === 'routes'} onClick={() => setWorkspace('routes')}>Tuyến đường</button>
          <button aria-pressed={workspace === 'stations'} onClick={() => setWorkspace('stations')}>Trạm dừng <span>{stations.length}</span></button>
        </div>
        <div className="context-content" hidden={workspace !== 'tracking' && workspace !== 'simulation'}>
          <FleetWorkspace liveSnapshot={live.snapshot} tripSelection={tripSelection} onTripCreated={handleTripCreated} onSimulateTrip={openSimulation} onFocusVehicle={focusVehicle} onToast={setToast} onFocusStop={focusLocation} onManageRoutes={() => selectMode('routes')} onManageStations={() => selectMode('stations')} />
        </div>
        <div className="context-content station-workspace" hidden={workspace !== 'stations'}>
          <div className="panel-list-slot" hidden={formMode !== 'closed' || selectedStation !== null}>
            <StationPanel stations={stations} selectedStationId={selectedStationId} loading={loadingStations} error={formMode === 'closed' ? stationError : null} selectionDisabled={formMode !== 'closed'} mode={formMode} onBeginCreate={handleBeginCreate} onSelect={handleSelectStation} onBeginEdit={handleBeginEdit} onDelete={setDeleteCandidate} />
          </div>
          <StationDrawer station={selectedStation} mode={formMode} form={stationForm} saving={savingStation} error={stationError} pickingLocation={pickingLocation}
            onClose={handleCloseStationDrawer} onBeginEdit={() => handleBeginEdit()} onPickLocation={() => { setPickingLocation(true); setActivePanel(null); }}
            onFieldChange={handleFieldChange} onSave={handleSaveStation} onRequestDeactivate={() => selectedStation && setDeleteCandidate(selectedStation)} />
        </div>
        <div className="context-content" hidden={workspace !== 'routes'}>
          <RouteWorkspace stations={stations} loadingStations={loadingStations} onPlannedRouteDisplay={setPlannedRoute} onShowToast={setToast}
            onDraftStopsChange={setDraftStops} selectedDraftStopId={selectedDraftStopId} onFocusDraftStop={focusDraftStop} onFocusStop={focusLocation} />
        </div>
        {(workspace === 'stations' || workspace === 'routes') && <div className="context-footer"><span className="status-dot" />{workspace === 'stations' ? 'Danh sách trạm đã lưu' : 'Quản lý tuyến đường'}</div>}
      </aside>

      <div className="operations-dock" data-map-edge="right" hidden={compact && activePanel !== 'simulator' && activePanel !== 'alerts'}>
        <div className="floating-panel glass-panel" hidden={compact && activePanel !== 'simulator'}>
          <div className="floating-panel-heading"><span><Play size={15} />MÔ PHỎNG XE</span><div>
            <button className="sheet-expand" onClick={() => setSheetExpanded(value => !value)} aria-label={sheetExpanded ? 'Thu chiều cao bảng' : 'Mở rộng bảng'}>{sheetExpanded ? <ChevronDown size={16} /> : <ChevronUp size={16} />}</button>
            <button aria-label={simulatorExpanded ? 'Thu bảng mô phỏng' : 'Mở bảng mô phỏng'} aria-expanded={simulatorExpanded}
              onClick={() => { if (compact) setActivePanel(null); else setSimulatorExpanded(value => !value); }}><ChevronDown size={16} /></button>
          </div></div>
          <div className="floating-panel-body" hidden={!simulatorExpanded}><SimulatorPanel key={`${simulator.tripId ?? 'none'}:${simulator.run?.attemptNumber ?? 1}`} simulator={simulator} snapshot={live.snapshot} now={live.now} connection={live.connection} connectionError={live.error} onReconnect={live.reconnect}
            fleet={workspace==='simulation' ? simulationFleet : undefined} onSelectVehicle={selectSimulationVehicle} onFitFleet={fitSimulationFleet}
            onManageFleet={()=>{setWorkspace('tracking');setDrawerOpen(true);setActivePanel('context');}}
            onShowRoute={() => {
            setWorkspace('simulation');
            if (simulator.trip) {
              const preview=simulationFleet.previews.find(item=>item.trip.id===simulator.tripId);
              if (preview) focusLocation([preview.start.latitude,preview.start.longitude],16);
              else { focusVehicle(simulator.trip.vehicleId); setFollowingVehicle(true); }
            }
          }} /></div>
        </div>
        <div className="floating-panel glass-panel alert-panel" hidden={compact && activePanel !== 'alerts'}>
          <div className="floating-panel-heading"><span><Bell size={15} />CẢNH BÁO</span><div><span className="count-badge">{live.snapshot?.notifications?.filter(item => !item.readAt).length ?? 0}</span>
            <button className="sheet-expand" onClick={() => setSheetExpanded(value => !value)} aria-label={sheetExpanded ? 'Thu chiều cao bảng' : 'Mở rộng bảng'}>{sheetExpanded ? <ChevronDown size={16} /> : <ChevronUp size={16} />}</button>
            <button aria-label={alertsExpanded ? 'Thu bảng cảnh báo' : 'Mở bảng cảnh báo'} aria-expanded={alertsExpanded}
              onClick={() => { if (compact) setActivePanel(null); else setAlertsExpanded(value => !value); }}><ChevronDown size={16} /></button>
          </div></div>
          <div className="floating-panel-body" hidden={!alertsExpanded}><AlertStream notifications={live.snapshot?.notifications ?? []} /></div>
        </div>
      </div>

      <div className="panel-launchers" data-map-edge="bottom" aria-label="Mở bảng công cụ">
        <button onClick={() => openPanel('context')} aria-pressed={contextVisible}><List size={16} /><span>{workspace === 'tracking' || workspace === 'simulation' ? 'Đội xe' : 'Tuyến & trạm'}</span></button>
        <button onClick={() => openPanel('simulator')} aria-pressed={compact ? activePanel === 'simulator' : simulatorExpanded}><Play size={16} /><span>Mô phỏng</span></button>
        <button onClick={() => openPanel('alerts')} aria-pressed={compact ? activePanel === 'alerts' : alertsExpanded}><Bell size={16} /><span>Cảnh báo</span></button>
      </div>

      <div className="map-picking-banner" role="status" hidden={!pickingLocation || workspace !== 'stations'}>
        <Crosshair size={17} /><span>Kéo bản đồ để đưa vị trí trạm vào tâm ngắm</span>
        <button className="picking-center-btn" onClick={handlePickMapCenter}>Chọn vị trí này</button>
        <button className="picking-cancel-btn" onClick={() => { setPickingLocation(false); openPanel('context'); }} aria-label="Hủy chế độ chọn vị trí"><X size={16} /></button>
      </div>
      <div className="station-center-target" hidden={!pickingLocation || workspace !== 'stations'} aria-hidden="true">
        <span />
      </div>

      <MapControls theme={theme} onThemeChange={setTheme}
        onResetCenter={() => focusLocation(HCMC_CENTER, 13)}
        onZoomIn={() => mapInstanceRef.current?.zoomIn()} onZoomOut={() => mapInstanceRef.current?.zoomOut()}
        onFit={handleFit} canFit={(workspace==='simulation' && fleetPoints.length>0) || (showStations && stations.length > 0) || (showRoutes && (plannedRoute !== null || draftStops.length > 0))}
        showStations={showStations} showRoutes={showRoutes} onToggleStations={() => setShowStations(value => !value)} onToggleRoutes={() => setShowRoutes(value => !value)}
        showTraffic={showTraffic} onToggleTraffic={() => setShowTraffic(value => !value)} trafficMessage={trafficMessage}
        trafficCanRetry={Boolean(traffic.error) || trafficStatus === 'UNAVAILABLE'} onRetryTraffic={traffic.refresh} />
      {deleteCandidate && <ConfirmStationDelete station={deleteCandidate} saving={deletingStation} onCancel={() => setDeleteCandidate(null)} onConfirm={handleDeactivate} />}
      {toast && <div className="application-toast" role="status">{toast}</div>}
    </main>
  );
};
